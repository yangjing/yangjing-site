title: 使用 clap 和 opendal 开发一个云存储 cli
tags:

- clap
- opendal
- rust
- tokio
- obs
- oss
- toml
- config
- 华为云
- 阿里云
date: 2024-06-17 16:22:56
category:

---

在使用 gitlab 做 CI/CD 时，需要将构建好的制品推送到云存储中（比如 [华为云 OBS](https://www.huaweicloud.com/product/obs.html)、[阿里云 OSS](https://www.aliyun.com/product/oss)、[AWS S3](https://aws.amazon.com/s3/) 等），然后在部署的时候再直接从云存储中下载。
为方便使用，就使用 clap 和 opendal 开发了一个简单的云存储命令行工具，此示例支持 OBS 和 OSS，需要添加其它云存储支持也非常方便，具体可以参考 [https://docs.rs/opendal/latest/opendal/services/](https://docs.rs/opendal/latest/opendal/services/)。

本文示例的完整代码见： [https://github.com/yangjing/technique-rust/tree/main/clis/storage-cli](https://github.com/yangjing/technique-rust/tree/main/clis/storage-cli)

## Clap

[https://docs.rs/clap/latest/clap/](https://docs.rs/clap/latest/clap/) 是一个非常强大的命令行参数解析库，可以非常方便的实现命令行参数解析。可以使用它提供的 `derive` 能力通过宏来快速定义一个命令行参数解析器。

### Clap 的 Parser 宏

首先来定义主结构：[`DevopsCmd`](https://github.com/yangjing/technique-rust/blob/main/clis/storage-cli/src/cmd/devops_cmd.rs)。 在`#[derive(Parser))]` 中添加 `Parser` 来自动实现命令行参数解析功能，可以通过不同的命令行参数配置存储服务、存储桶、认证信息及通过配置文件指定等。

```rust
#[derive(Debug, Parser)]
#[command(name = "devops-cli")]
#[command(version, about = "DevOps command tool")]
pub struct DevopsCmd {
    #[arg(short, long, default_value_t = StorageSource::Obs)]
    pub service: StorageSource,

    #[arg(short, long)]
    pub bucket: Option<String>,

    #[arg(long)]
    pub ak: Option<String>,

    #[arg(long)]
    pub sk: Option<String>,

    #[arg(short('f'), long)]
    pub config_file: Option<String>,

    #[command(subcommand)]
    pub file_op: Option<FileOperation>,
}
```

#### 结构体属性

`#[derive(Debug, Parser)]`：这个属性让Rust编译器为DevopsCmd结构体自动实现Debug trait（便于调试打印）和Parser trait（由clap库提供，用于解析命令行参数）

#### CLI元数据

- `#[command(name = "devops-cli")]`：定义了命令行工具的名字为devops-cli。
- `#[command(version, about = "DevOps command tool", long_about = None)]`：提供了关于程序的简短描述（"DevOps command tool"）以及版本信息（需在Cargo.toml中定义版本号）。long_about未指定，意味着不提供更详细的帮助信息。

#### 结构体字段（命令行参数）

##### `arg`

注解中的 `short` 将生成短参数 `-s`，`long` 将生成长参数 `--service`，当用户未指定时通过 `default_value_t` 指定默认值为 `StorageSource::Obs`。

##### `short('f')`

`short` 允许添加参数来自定义生成的短参数使用什么字符（这里需要注意的是它的参数类型是 `char`，而不是 `String`）。

##### `command`

通过 `command` 定义子命令，很快我们就可以看到它的定义方式。它用于进一步选择与文件操作相关的子命令。

### 通过 Subcommand 定义子命令

```rust
#[derive(Debug, Subcommand)]
pub enum FileOperation {
    Put { src: String, object_key: String },
    Get { object_key: String, dst: String },
    Stat { object_key: String },
}
```

这个代码定义了一个名为 `FileOperation` 的枚举类型，用于表示文件操作的三种类型：`Put`、`Get`和`Stat`。每个类型都是一个结构体，包含不同的字段。`Put` 操作需要一个源文件路径 `src` 和目标对象键 `object_key`，`Get` 操作需要一个对象键`object_key` 和目标文件路径 `dst`，`Stat` 操作只需要一个对象键 `object_key`。这个枚举类型通过 `Subcommand` 宏为 `FileOperation` 自动实现了 [`Subcommand`](https://docs.rs/clap/latest/clap/trait.Subcommand.html) trait。

### 运行程序

通过 `-h` 打印帮助信息：

```shell
cargo -q run -p storage-cli --bin devops-cli -- -h
```

输出：

```shell
DevOps command tool

Usage: devops-cli [OPTIONS] [COMMAND]

Commands:
  put
  get
  stat
  help  Print this message or the help of the given subcommand(s)

Options:
  -s, --service <SERVICE>          [default: obs] [possible values: obs, oss]
  -b, --bucket <BUCKET>
      --ak <AK>
      --sk <SK>
  -f, --config-file <CONFIG_FILE>
  -h, --help                       Print help (see more with '--help')
  -V, --version                    Print version
```

## OpenDAL

OpenDAL 对各类存储进行了很好的封装，对常用文件操作进行了统一的 API 抽象，如 OBS 支持度如下：

- [x] stat
- [x] read
- [x] write
- [x] create_dir
- [x] delete
- [x] copy
- [ ] rename
- [x] list
- [x] presign
- [ ] blocking

### 创建 `Operator`

通过 `Obs` 构建器（`Obs` 是 `ObsBuilder` 的一个类型别名）来设置配置参数，然后通过 `Operator::new` 来构造一个统一的 `Operator` 访问云存储。

```rust
let mut b: Obs = Obs::default();
b.bucket(&sc.bucket)
    .endpoint(&sc.endpoint)
    .access_key_id(&sc.ak)
    .secret_access_key(&sc.sk);
let op = Operator::new(b)?;
```

### 处理子命令（FileOperation）

```rust
impl FileOperation {
    pub async fn execute(&self, op: &Operator) -> Result<()> {
        match self {
            FileOperation::Put { src, object_key } =>
                put_src_to_object_key(op, src, object_key).await?,
            FileOperation::Get { object_key, dst } =>
                get_object_key_to_dst(op, object_key, dst).await?,
            FileOperation::Stat { object_key } =>
                dump_stat(op, object_key).await?,
        }
        Ok(())
    }
}
```

上面这段代码比较简单，就是对 `FileOperation` 类型的枚举值进行匹配，然后调用对应的函数进行处理。

### 文件操作处理函数

#### 上传本地文件到对象存储

```rust
async fn put_src_to_object_key(op: &Operator, src: &str, object_key: &str) -> Result<()> {
    use futures::AsyncWriteExt;

    let mut f = File::open(src).await?;
    let mut writer = op.writer_with(object_key).await?.into_futures_async_write();
    let mut buf = [0_u8; 8192];
    let mut uploaded = 0;

    loop {
        let n = f.read(&mut buf[..]).await?;
        if n == 0 {
            break;
        }
        writer.write_all(&buf[..n]).await?;
        uploaded += n;
    }
    writer.close().await?;

    info!("Total file upload of {} bytes.", uploaded);
    Ok(())
}
```

#### 下载对象存储文件到本地

```rust
async fn get_object_key_to_dst(op: &Operator, object_key: &str, dst: &str) -> Result<()> {
    use tokio::io::AsyncWriteExt;

    let mut f = File::create_new(dst).await?;
    let reader = op.reader_with(object_key).await?;
    let mut readed = 0u64;

    let mut bs = reader.into_bytes_stream(..).await?;
    while let Ok(Some(item)) = bs.try_next().await {
        if item.is_empty() {
            break;
        }
        readed += item.len() as u64;
        f.write_all(&item).await?;
    }

    info!("Total file download of {} bytes.", readed);
    f.flush().await?;
    Ok(())
}
```

#### 打印文件元信息

通过 `op.stat(object_key).await?;` 函数可以获取文件元信息，然后通过 `println!` 函数打印出来。具体代码见： [https://github.com/yangjing/technique-rust/blob/main/clis/storage-cli/src/cmd/file_operation.rs](https://github.com/yangjing/technique-rust/blob/main/clis/storage-cli/src/cmd/file_operation.rs) 的 `dump_stat` 函数。

### 编译执行

```shell
cargo build --release

# 上传文件
RUST_LOG=debug ./target/release/devops-cli -f ./clis/storage-cli/.app.toml put ./target/release/devops-cli software/devops-cli

# 下载文件
RUST_LOG=debug ./target/release/devops-cli -f ./clis/storage-cli/.app.toml get software/devops-cli devops-cli

# 查询文件元数据
./target/release/devops-cli -f ./clis/storage-cli/.app.toml stat software/devops-cli
#chmod +x devops-cli && devops-cli -f ./clis/storage-cli/.app.toml stat software/devops-cli
```

## 解析配置文件

上面命令行参数 `-f` 读取的 `.app.toml` 配置文件内容如下：

```toml
# source = "obs"

[storage]
ak = "<ak>"
sk = "<sk>"
endpoint = "obs.cn-southwest-2.myhuaweicloud.com"
bucket = "<bucket>"
```

通过以下代码解析配置文件：

```rust
#[derive(Debug, Deserialize)]
pub struct DevopsConf {
    service: StorageSource,
    storage: Option<StorageConf>,
}

#[derive(Debug, Deserialize)]
pub struct StorageConf {
    pub endpoint: String,
    pub bucket: String,
    pub ak: String,
    pub sk: String,
}

impl DevopsConf {
    pub fn from_devops_cmd(cmd: &DevopsCmd) -> Result<Self> {
        let mut cb = config::Config::builder();
        if let Some(config_file) = cmd.config_file.as_deref() {
            cb = cb.add_source(config::File::with_name(config_file));
        }

        match std::env::var("SERVICE") {}

        if std::env::var("SERVICE").iter().any(|s| s.is_empty()) {
            // 当环境变量 SERVICE 未设置时
            if let Some(value) = cmd.service.as_ref() {
                // 当命令行参数 service 设置时
                std::env::set_var("SERVICE", value.to_string());
            }
        }
        if let Some(ak) = cmd.ak.as_deref() {
            std::env::set_var("STORAGE__AK", ak);
        }
        if let Some(sk) = cmd.sk.as_deref() {
            std::env::set_var("STORAGE__SK", sk);
        }
        if let Some(bucket) = cmd.bucket.as_deref() {
            std::env::set_var("STORAGE__BUCKET", bucket);
        }
        let v = cb
            .add_source(config::Environment::default().separator("__"))
            .build()?
            .try_deserialize()?;
        Ok(v)
    }

    // ....
}
```

首先通过 `cmd.config_file` 判断是否指定配置文件，如果指定了，则读取配置文件，否则需要在命令行指定所有需要的参数。无论是否指定配置文件，都将读取命令行参数，并使用 `std::env::set_var` 设置环境变量。然后再通过 `cb.add_source(config::Environment::default())` 将环境变量作为配置源添加到配置对象中。这样：

1. 无论是否指定配置文件，命令行参数都会覆盖配置文件中的配置；
2. 若通过环境变量指定值，则命令行参数和配置文件中的配置都会被覆盖。如，我们可以通过如下方式使用环境变量覆盖命令行参数以及配置文件中的配置：

    ```shell
    RUST_LOG=debug SERVICE=obs STORAGE__BUCKET=file-001 ./target/release/devops-cli -f ./clis/storage-cli/.app.toml stat software/devops-cli
    ```

_**注意** `.add_source(config::Environment::default().separator("__"))` 这里给 `separator` 的参数是两个下划线。这样在命令行参数指定有层级的 KEY 时就可以避免一个下划线被识别为分隔符（默认值为一个下划线）。比如上面的的 `STORAGE__BUCKET` 就会被解析为 `toml` 配置的 `storage.bucket`。_

## 小结

今天演示了 clap、opendal、config 3个库的用法，通过这些库，我们可以轻松实现一个简单的云存储客户端。
