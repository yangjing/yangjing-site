title: RAG 系统开发 01：使用 rig 调用 ollama 的模型
date: 2025-02-12 09:31:30
category: ai
tags: ["rag", "ollama", "rig", "rust"]

---

这是个系列文章，将介绍基于 Rust 语言生态来开发一个 RAG 系统。本文是文章的第一篇，主要介绍如何使用 [rig](https://crates.io/crates/rig-core) 来调用 ollama 模型。

## 项目准备

### 设置 Rust 开发环境

推荐使用 [RsProxy](https://rsproxy.cn/) 来设置 Rust 开发环境，步骤非常的简单：

1. 设置 Rustup 镜像， 修改配置 `~/.zshrc` 或 `~/.bashrc`

```shell
export RUSTUP_DIST_SERVER="https://rsproxy.cn"
export RUSTUP_UPDATE_ROOT="https://rsproxy.cn/rustup"
```

2. 安装 Rust（请先完成步骤一的环境变量导入并 source rc 文件或重启终端生效）

```shell
curl --proto '=https' --tlsv1.2 -sSf https://rsproxy.cn/rustup-init.sh | sh
```

3. 设置 crates.io 镜像， 修改配置 `~/.cargo/config.toml`：

```toml
[source.crates-io]
replace-with = 'rsproxy-sparse'
[source.rsproxy]
registry = "https://rsproxy.cn/crates.io-index"
[source.rsproxy-sparse]
registry = "sparse+https://rsproxy.cn/index/"
[registries.rsproxy]
index = "https://rsproxy.cn/crates.io-index"
[net]
git-fetch-with-cli = true
```

### 安装 ollama 并下载模型

详细安装及使用可我之前文章： [本地运行 deepseek-r1，LLM 安装简明指南](https://yangjing.github.io/2025/02/09/%E6%9C%AC%E5%9C%B0%E8%BF%90%E8%A1%8C-deepseek-r1%EF%BC%8CLLM-%E5%AE%89%E8%A3%85%E7%AE%80%E6%98%8E%E6%8C%87%E5%8D%97/#Ollama)

### 创建项目

开发工具建议使用 [VSCode](https://code.visualstudio.com/)，并安装插件 [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)。在命令行终端执行以下命令创建 Rust 项目并添加必要的 crates：

```shell
cargo new fusion-rag
cd fusion-rag
cargo add rig-core --features derive
cargo add tokio --features full
```

现在项目已经建好，可以通过 VSCode 打开

```shell
code .
```

执行默认的 `main.rs` 文件，可以运行成功。

![fusion-rag with vscode](/img/rag/fusion-rag.png)

## 使用 rig-core

### 通过 openai 兼容模式访问 ollama API

编辑 `main.rs` 文件，修改为以下代码：

```rust
use rig::{completion::Prompt, providers};

#[tokio::main]
async fn main() -> Result<(), Box<dyn core::error::Error>> {
    let client = providers::openai::Client::from_url("ollama", "http://localhost:11434/v1");
    let v1 = client
        .agent("qwen2.5:latest") // .agent("deepseek-r1:latest")
        // preamble 用于设置对话的 `system` 部分，通常设置为聊天上下文的提示语
        .preamble("你人工智能助手，你更擅长逻辑推理以及中文和英文的对话。")
        .build();

    // prompt 用于设置对话的 `user` 部分，用于提供每次对话的内容
    let response = v1.prompt("1.1 和 1.11 哪个大？").await?;
    println!("回答: {}", response);
    Ok(())
}
```

运行程序，可获得如下输出：

```markdown
回答: 在数值比较中，1.1 和 1.11 进行比较时，可以看出 1.11 比 1.1 要大。

数学上具体的比较过程如下：

- 首先比较小数点后的第一位数字。在这个例子中都是“1”，所以这一位是相等的。
- 然后继续比较下一位，也就是第二个小数点后的数字。对于 1.1 而言，这一步之后没有数字，所以我们假定为 0（在实际中通常会以零补齐），因此可以认为 1.1 相当于 1.10。这时候我们可以看到在“1.10”和“1.11”之间进行比较，“1.11”的结果显然比“1.10”大。

所以，结论是：1.11 大于 1.1。
```

> 提示：使用 `deepseek-r1:latest` 模型可以获得更详细的回答（包含思考过程），但需要的资源更多且输出的内容也会更长。读者可以自行选择适合自己的模型。

### 通过嵌入模型实现 RAG

#### nomic-embed-text 模型

`nomic-embed-text` 是专门用于生成文本嵌入（text embeddings）的模型。文本嵌入是将文本数据转换为向量表示的过程，这些向量能够捕捉文本的语义信息，在很多自然语言处理任务中都非常有用，例如信息检索（找到与查询文本语义相近的文档）、文本分类、聚类分析等。可通过以下命令下载此模型。

```shell
ollama pull nomic-embed-text
```

#### 添加 crates 依赖

```shell
cargo add serde
```

#### 实现 RAG 逻辑

编辑 `main.rs` 文件，更新为以下代码：

```rust
use rig::{
    completion::Prompt, embeddings::EmbeddingsBuilder, providers,
    vector_store::in_memory_store::InMemoryVectorStore, Embed,
};
use serde::Serialize;

// 需要进行 RAG 处理的数据。需要对 `definitions` 字段执行向量搜索，
// 因此我们为 `WordDefinition` 标记 `#[embed]` 宏以派生 `Embed` trait。
#[derive(Embed, Serialize, Clone, Debug, Eq, PartialEq, Default)]
struct WordDefinition {
    id: String,
    word: String,
    #[embed]
    definitions: Vec<String>,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn core::error::Error>> {
    const MODEL_NAME: &str = "qwen2.5";
    const EMBEDDING_MODEL: &str = "nomic-embed-text";
    let client = providers::openai::Client::from_url("ollama", "http://localhost:11434/v1");
    let embedding_model = client.embedding_model(EMBEDDING_MODEL);

    // 使用指定的嵌入模型为所有文档的定义生成嵌入向量
    let embeddings = EmbeddingsBuilder::new(embedding_model.clone())
        .documents(vec![
            WordDefinition {
                id: "doc0".to_string(),
                word: "flurbo".to_string(),
                definitions: vec![
                    "1. *flurbo* （名词）：flurbo是一种生活在寒冷行星上的绿色外星人。".to_string(),
                    "2. *flurbo* （名词）：一种虚构的数字货币，起源于动画系列《瑞克和莫蒂》。".to_string()
                ]
            },
            WordDefinition {
                id: "doc1".to_string(),
                word: "glarb glarb".to_string(),
                definitions: vec![
                    "1. *glarb glarb* （名词）：glarb glarb是次郎星球居民祖先用来耕种土地的古老工具。".to_string(),
                    "2. *glarb glarb* （名词）：一种虚构的生物，发现于仙女座星系Glibbo星球遥远的沼泽地。".to_string()
                ]
            },
        ])?
        .build()
        .await?;

    // 使用这些嵌入创建向量存储
    let vector_store = InMemoryVectorStore::from_documents(embeddings);

    // 创建向量存储索引
    let index = vector_store.index(embedding_model);

    let rag_agent = client
        .agent(MODEL_NAME)
        .preamble(
            "您是这里的词典助理，帮助用户理解单词的含义。
            您将在下面找到其他可能有用的非标准单词定义。",
        )
        .dynamic_context(1, index)
        .build();

    // 提示并打印响应
    let response = rag_agent.prompt("\"glarb glarb\" 是什么意思？").await?;
    println!("{}", response);

    Ok(())
}
```

先运行程序看看效果，可获得如下输出：

```shell
$ cargo run -q
在给出的定义中，“glarb glarb”有以下两种解释：

1. **名词**: 这是次郎星球居民祖先用来耕种土地的古老工具。
2. **名词**: 一种虚构的生物，发现于仙女座星系Glibbo星球遥远的沼泽地。

请注意，这是基于提供的文档定义，“glarb glarb”可能是两个不同的名词，具有不同的含义和背景。
```

当我们注释掉 `.dynamic_context(1, index)` 一行时再次运行，输出结果如下：

```shell
$ cargo run -q
很抱歉，“glarb glarb”并不是一个已知的词语或表达方式，在标准语言中没有明确的意义。这可能是误输入或者是某种特定情境下的自创语句。具体含义需要更多上下文信息来确定。如果您是在某个游戏中、书中或是特殊社群里看到这个短语，可能需要参照该环境中的规则或解释。
```

可以看到，我们在使用 `.dynamic_context(1, index)` 函数后，这个函数会根据输入的提示，从向量存储中搜索最相似的文档，然后将这些文档添加到提示中，从而实现 RAG（Retrieval Augmented Generation）的效果。

## 小结

本文简单的介绍了如何使用 `rig-core` 库来使用 Ollama 模型，并展示了如何使用 `rig-core` 库来使用 Ollama 模型进行 RAG 的实现。这是一个基本的示例，实际应用中可能需要根据需求进行一些调整和扩展。后面会有更详细的介绍和示例，比如：文档（PDF、Word、excel、PPT）解析、数据持久化存储、……敬请期待。
