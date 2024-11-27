title: Rust gRPC 编译手册：tonic-build
date: 2024-09-25 08:02:30
category: rust
tags: grpc, rust, tonic, prost, protobuf
---

前面章节已经简单介绍了 `tonic-build` 的使用，本节将深入 `tonic-build`，详细介绍在编译 .proto 文件时可提供的定制功能。

## 安装

### cargo

使用 `tonic-build` 需要在 `Cargo.toml` 中配置以下依赖

```toml
[dependencies]
tonic = "0.12"
prost = "0.13"

[build-dependencies]
tonic-build = "0.12"
```

`prost` 提供了 protobuf 的支持，包括 protobuf 数据序列化，预定义的 `google.protobuf.` 数据类型等。`tonic` 提供了 gRPC 服务端/客户端支持，它基于 Axum 框架实现，可以复用 `tower` 生态提供的众多组件。

### proto 文件

通常，proto 文件放在包（*cargo 术语，可以理解成“项目”/“子项目”的意思*）根目录下 `proto` 目录中，例如项目目录为 `tonic-getting`，则 `proto` 目录结构如下：

```sh
└── tonic-getting
    ├── Cargo.toml
    ├── README.md
    ├── build.rs
    ├── proto
    ├── rustfmt.toml
    ├── src
    └── target
```

### IDE 设置

#### VSCode

在 VSCode 中使用 [rust-analyzer](https://rust-analyzer.github.io/) 时，启用 `"rust-analyzer.cargo.buildScripts.enable": true` 可以让 IDE 正确的识别生成的代码。你可以编辑 `.vscode/settings.json` 文件添加如下内容设置：

```json
{
  "rust-analyzer.cargo.buildScripts.enable": true
}
```

## `build.rs`

cargo 提供了 `build.rs` 文件，用于在编译时执行自定义的构建脚本。如：链接到 C 库、生成代码等。先来看一个示例构建脚本，然后再来详细了解 tonic-build 提供的各个选项。

```rust
use std::{env, path::PathBuf};

fn main() {
  let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

  tonic_build::configure()
    // 现实启用当 .proto 文件变化时自动重编译
    .emit_rerun_if_changed(true)
    // 生成 gRPC 服务端代码，默认为 true
    .build_server(true)
    // 生成 gRPC 客户端代码，默认为 true
    .build_client(true)
    // 生成描述符文件，当使用 gRPC Reflection 功能时可以从这个文件中获取服务描述信息来返回给调用方
    .file_descriptor_set_path(out_dir.join("getting_descriptor.bin"))
    .compile(
      &[
        "proto/getting/basic.proto",
        "proto/getting/common/page.proto",
        "proto/getting/v1/auth.proto",
        "proto/getting/v1/user.proto",
      ],
      &["proto"],
    )
    .unwrap();
}
```

上面示例代码对部分常用选项进行了说明。`tonic-build` 提供了 `Builder` 类型，用于配置编译选项，后文对一些可能用到的重要选项进行说明。完整说明文档可以参考 [tonic-build Builder](https://docs.rs/tonic-build/0.12.2/tonic_build/struct.Builder.html) 和 [prost-build Config](https://docs.rs/prost-build/latest/prost_build/struct.Config.html) 。tonic 对 protobuf 消息的编译选项也由 `prost-build` 提供，查看代码的话会发现它内部调用了 `prost-build` 的 [`compile_protos`](https://docs.rs/prost-build/latest/prost_build/struct.Config.html#method.compile_protos) 方法。

`OUT_DIR` 环境变量是 cargo 预定义的代码生成输出目录，从 `.proto` 编译的代码将生成到该目录中。

### 常用 Builder 选项说明

#### `.file_descriptor_set_path`

由 `protoc` 生成的 `FileDescriptorSet` 将写入此路径。***这里注意，我们应该先获取 `OUT_DIR` 目录，再拼接文件名获得输出路径，不然文件将被写入到包根目录中。***

#### `.out_dir`

设置输出目录以生成代码。*默认为 `OUT_DIR` 环境变量指定目录，`OUT_DIR` 环境变量在编译时由 cargo 自动设置，因此通常不需要配置此选项。*

#### `.extern_path`

声明外部提供的 protobuf 包或类型。例如，我们有 `ultimate_api.page.Pagination` 和 `ultimate_api.Empty` 类型，我们可以通过如下配置如它使用已定义的 `ultimate_api::page::Pagination` 和 `ultimate_api::Empty` 类型。

```rust
  .extern_path(".ultimate_api", "::ultimate_api");
```

这里需要注意的是，第一个参数指定 proto `packapge` 路径前缀时需要带 `.`，例如 `.ultimate_api`；第二个参数指定生成的 Rust 类型模块路径前缀，建议带 `::` 来避免当前 `crate` 下有命名冲突。

#### `.btree_map`

`.btree_map` 有一个 `paths` 参数，指向特定字段、消息或包的路径。

> 后面的 `.bytes` 和几个 `.xxx_attribute` 等选项的路径参数设置类似。

配置代码生成器为指定路径的字段且为 protobuf `map` 类型生成 [BTreeMap](https://doc.rust-lang.org/std/collections/struct.BTreeMap.html) 类型。这里的路径是一个路径前缀，既只要以此路径前缀匹配的字段都将生成 `BTreeMap` 类型。路径参数要以 `.` 开头，若只设置为 `.` 则表示所有 `map` 类型都成成为 `BTreeMap`。

这里给出一些示例：

```rust
// 匹配字段
config.btree_map(&[".my_messages.MyMessageType.my_map_field"]);

// 匹配消息类型
config.btree_map(&[".my_messages.MyMessageType"]);

// 匹配 package 下的所有消息类型的设置为 map 类型的字段
config.btree_map(&[".my_messages"]);
```

#### `.bytes`

为 protobuf 的 `bytes` 类型生成 Rust `bytes::Bytes` 类型字段。需要添加 `bytes` crate（`cargo add bytes`）。

#### `.type_attribute`

为匹配的 `message`、`enum` 和 `oneof` 添加额外属性。有两个参数：

- `paths`: `P: AsRef<str>` 的配置同上，也是一个前缀路径。
- `attribute`: `A: AsRef<str>` 是要添加的属性，例如 `"#[derive(Eq)]"`。所有属性都是附加的，不会替换之前配置的任何属性，所以有可能触发编译器提示属性重复错误。

示例：

```rust
// 为所有类型添加 `PartialEq`
config.type_attribute(".", "#[derive(Eq)]");
// 为消息添加 `serde` 序列化支持
config.type_attribute("my_messages.MyMessageType",
                      "#[derive(Serialize)] #[serde(rename_all = \"snake_case\")]");
config.type_attribute("my_messages.MyMessageType.MyNestedMessageType",
                      "#[derive(Serialize)] #[serde(rename_all = \"snake_case\")]");
```

由于 `oneof` 字段在 protobuf 中没有自己的类型名称，因此字段名称可以同时与 `type_attribute` 和 `field_attribute` 一起使用。一个放在 `enum` 类型定义之前，另一个放在相应消息 `struct` 中字段之前。

#### `.message_attribute`

只向匹配的消息添加额外属性。

#### `.enum_attribute`

只向匹配的枚举添加额外属性。示例：

```rust
// 为枚举添加 serde_repr，以匹配 Rust 的 repr 特性，以使用整形值（通常是 `i32`）进行序列化
config.enum_attribute("my_messages.MyEnumType", "#[derive(serde_repr::Serialize_repr, serde_repr::Deserialize_repr)]")
```

#### `.field_attribute`

只向匹配的字段添加额外属性。

#### `.protoc_arg`

配置 `protoc` 的参数。例如，要启用 `--experimental_allow_proto3_optional` 参数。

#### `.compile`

方法（`.compile(protos: &[impl AsRef<Path>], includes: &[impl AsRef<Path>]) -> Result<()>` ）接受两个参数，`protos` 和 `includes`，说明如下：

- `protos`：要编译的 proto 文件列表，任何间接导入的 .proto 文件都将自动包含在内。
- `includes`：搜索导入的目录路径，目录按顺序搜索。传递给 `protos`（前一个参数）的 `.proto` 文件必须在提供的包含目录之一中找到。

## 导入生成代码到项目

`tonic` 从 .proto 文件编译生成的 Rust 代码将输出到 `OUT_DIR` 目录（默认在 `target/<debug/release>/build/<crate_name>-<hash>/out` 目录），需要引入源码路径（`src`目录内）才能编译到程序中。可以通过 `tonic::include!` 宏引入生成的代码。

```rust
pub mod getting {
  tonic::include_proto!("getting");
  pub mod common {
    tonic::include_proto!("getting.common");
  }
  pub mod v1 {
    tonic::include_proto!("getting.v1");
  }
}
```

这里引入了 3 个模块，每个模块都包含 `.proto` 文件中定义的 protobuf 消息类型。`tonic-build`（内部调用`prost-build`）会按 protobuf `package` 路径生成对于添加 `.rs` 后缀的 Rust 代码文件。

- `package getting;`（路径下有代码）将生成 `getting.rs` Rust 代码文件
- `package getting.common;` （路径下有代码）将生成 `getting.common.rs` Rust 代码文件
- `package getting.v1;` （路径下有代码）将生成 `getting.v1.rs` Rust 代码文件

> `tonic` 生成的代码里面不会应用 protobuf `package`，也就是不会生成对应的 Rust `mod` 路径。我们需要自己定义 Rust `mod` 的层次关系，就像这里代码里的 `pub mod getting` 和内部的 `pub mod common` 以及 `pub mod v1`。

## 高级技巧

### 自行映射 prost 类型

prost 采用了宏来实现与 protobuf 数据的转换。因此，我们可以先定义 Rust `struct`/`enum`，而非先定义 .proto 消息再生成 Rust 代码。这在定义要在多个项目中复用的基础数据结构时很有用（*比如 `google.protobuf.` 包下的消息就是这样定义的*）。要使用这个功能，需要添加 `prost` 模块。

我们有一个 `Pagination` 类型，提供分页请求参数。它在很多 gRPC API 里都有使用，特别是在一些工具类，甚至数据库帮助方法里都有使用。那这样，由每个引入 .proto（比如：`page.proto`）文件的项目都生成各自的 Rust 类型，这样是不利于复用的，而且也会在调用工具类和数据库帮助方法里多一次类型映射。因为 `prost` 通过 `derive` 宏来实现对 protobuf 的二进制序列化，我们可以定义的消息。

```rust
use serde::{Deserialize, Serialize};

#[derive(Clone, PartialEq, ::prost::Message, Serialize, Deserialize)]
pub struct Pagination {
  #[prost(int64, tag = "1")]
  pub page: i64,

  #[prost(int64, tag = "2")]
  pub page_size: i64,

  #[serde(default = "default_sort_bys")]
  #[prost(message, repeated, tag = "3")]
  pub sort_bys: ::prost::alloc::vec::Vec<SortBy>,

  #[serde(skip_serializing_if = "Option::is_none")]
  #[prost(int64, optional, tag = "4")]
  pub offset: ::core::option::Option<i64>,
}

fn default_sort_bys() -> Vec<SortBy> {
  vec![]
}
```

*完整代码见： [https://github.com/yangjing/ultimate-common/blob/main/crates/ultimate-api/src/v1/page.rs](https://github.com/yangjing/ultimate-common/blob/main/crates/ultimate-api/src/v1/page.rs)*

在 `struct` 的 `derive` 上添加 `Clone, PartialEq, ::prost::Message` 以支持 protobuf 二进制序列化。其它的宏可以根据项目需要自行添加。在 `build.rs` 里配置 `.extern_path(".ultimate_api", "::ultimate_api");` 后，`tonic-build` 就不会生成相应的 Rust 类型，而是直接使用已存在的 `::ultimate_api` 路径开头的类型。

在字段上通过 `prost` 宏设置对应 protobuf 的字段类型、字段编号、标记修饰（如：`repeated`、`optional`）。

## 小结

本文讨论了如何使用 `tonic-build` 生成 gRPC 服务的 Rust 代码，以及如何使用 `prost` 生成自定义类型。`tonic` 提供了丰富的配置选项，可以让我们控制生成代码的方式，如：添加自定义属性、自定义类型、是否生成服务端/客户端代码等。
