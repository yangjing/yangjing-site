title: Rust 过程宏：实现 Builder（二）
date: 2024-12-18 11:48:35
category: rust
tags: [rust, macros, proc-macro, syn, quote]

---

_这是 Rust 过程宏系列文章的第二篇，上一篇文章见：[Rust 过程宏：实现 Builder（一）](/2024/12/17/Rust-过程宏：实现-Builder（一）) 。_

## 05 Method Chaining

回顾前面 [04 Call build]()，我们通过单独的语句调用 `build` 方法来生成结构体。

```rust
  let command = builder.build().unwrap(); // 这里调用 build 生成 Command 对象
```

但有时候我们更想要在链式调用中生成结构体，例如：

```rust
  let command = Command::builder()
    .executable("cargo".to_owned())
    .args(vec!["build".to_owned(), "--release".to_owned()])
    .env(vec![])
    .current_dir("..".to_owned())
    .build()
    .unwrap();
```

当我们执行这段代码时，编译器提示如下错误：

```shell
┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈
error[E0507]: cannot move out of a mutable reference
  --> tests/05-method-chaining.rs:15:17
   |
15 |     let command = Command::builder()
   |  _________________^
16 | |     .executable("cargo".to_owned())
17 | |     .args(vec!["build".to_owned(), "--release".to_owned()])
18 | |     .env(vec![])
19 | |     .current_dir("..".to_owned())
   | |_________________________________^ move occurs because value has type `CommandBuilder`, which does not implement the `Copy` trait
20 |       .build()
   |        ------- value moved due to this method call
   |
note: `CommandBuilder::build` takes ownership of the receiver `self`, which moves value
  --> tests/05-method-chaining.rs:6:10
   |
6  | #[derive(Builder4)]
   |          ^^^^^^^^
note: if `CommandBuilder` implemented `Clone`, you could clone the value
  --> tests/05-method-chaining.rs:6:10
   |
6  |   #[derive(Builder4)]
   |            ^^^^^^^^ consider implementing `Clone` for this type
...
15 |     let command = Command::builder()
   |  _________________-
16 | |     .executable("cargo".to_owned())
17 | |     .args(vec!["build".to_owned(), "--release".to_owned()])
18 | |     .env(vec![])
19 | |     .current_dir("..".to_owned())
   | |_________________________________- you could clone this value
   = note: this error originates in the derive macro `Builder4` (in Nightly builds, run with -Z macro-backtrace for more info)
┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈
```

错误原因是调用 `build(self)` 方法时，会尝试移动 `CommandBuilder` 的所有权，导致编译错误。Rust 编译器也提供了一个解决方案：为 `CommandBuilder` 实现 `Clone` 特性，在调用 `build` 方法前克隆对象。但 `Clone` 构建器不是我们想要的，因为你不假定所有的属性都有实现 `Clone`。那有什么方法可以在 `build(&mut self)` 方法以通过引用的方式来获取 `CommandBuilder` 的属性的所有权呢？

答案是通过 `take()`，它可以获取 `Option` 的值（所有权）并使用 `None` 来替换。下面贴出 `make_build_fn` 函数的完整代码，并对新添加的 `take` 调用部分进行注释。

```rust
fn make_build_fn(vis: &Visibility, input_ident: &Ident, fields: &[BuilderField]) -> TokenStream2 {
  let required_field_checks = fields.iter().filter_map(|field| {
    let ident = &field.ident;
    match &field.ty {
      FieldType::Plain(_) => Some(quote! {
        // 通过 take() 获取值并清空，同时检查是否为 None
        let #ident = self.#ident.take().ok_or_else(|| {
          Box::<dyn Error>::from(format!("value is not set: {}", stringify!(#ident)))
        })?;
      }),
      FieldType::Optional(_) => None,
    }
  });

  let field_assignment = fields.iter().map(|field| {
    let ident = &field.ident;
    let expr = match &field.ty {
      FieldType::Plain(_) => quote!(#ident),
      FieldType::Optional(_) => quote!(self.#ident.take()), // 通过 take() 获取值并清空
    };
    quote! {
      #ident: #expr,
    }
  });

  quote! {
    #vis fn build(&mut self) -> Result<#input_ident, Box<dyn Error>> {
      #(#required_field_checks)*

      Ok(#input_ident {
        #(#field_assignment)*
      })
    }
  }
}
```

可以看到，代码与 [04 Call build]() 的区别非常小，只需要在使用 `self.#ident` 的调用时添加 `.take()` 即可。

> 注意：使用 `.take()` 后，`self.#ident` 将被清空，所以后续再调用 `build()` 时会报错。也就是说，我们生成的 `CommandBuilder` 只能被 `build` 一次。

## 06 Optional field

对于 Optional 字段，构建器应确保在未设置相应字段的情况下调用 `build` 不会出错，相关测试代码如下：

```rust
  let command = Command::builder()
    .executable("cargo".to_owned())
    .args(vec!["build".to_owned(), "--release".to_owned()])
    .env(vec![])
    .build()
    .unwrap();
  assert!(command.current_dir.is_none());
```

对于 Optional 字段的处理，这里没有新代码，前面的宏代码已经实现，这里我们回过头来解读下对于构建器的 `struct` 构造、初始化和 `build` 中怎么处理 `Option` 的。

对于 `CommandBuilder` struct 的 `Option`，有两种情况。一种是属性本身就是 `Option<T>`，比如 `current_dir: Option<String>>`，这种情况下，在 `build` 方法中，直接使用 `self.current_dir.take()` 即可。而另一种情况是，属性是 `T`，比如 `executable: String`，这种情况下就需要做特殊处理。

回看 `make_build_fn` 代码的 `required_field_checks` 部分：

```rust
fn make_build_fn(vis: &Visibility, input_ident: &Ident, fields: &[BuilderField]) -> TokenStream2 {
  let required_field_checks = fields.iter().filter_map(|field| {
    let ident = &field.ident;
    match &field.ty {
      FieldType::Plain(_) => Some(quote! {
        // 通过 take() 获取值并清空，同时检查是否为 None
        let #ident = self.#ident.take().ok_or_else(|| {
          Box::<dyn Error>::from(format!("value is not set: {}", stringify!(#ident)))
        })?;
      }),
      FieldType::Optional(_) => None,
    }
  });

  // ...
}
```

我们对 `&field.ty` 进行模式匹配，对 `FieldType::Plain(_)` 类型的字段进行了值是否设置检查。通过 `ok_or_else` 函数，当字段未被设置时设置对应的 `Error` 信息并使用 `?` 操作符提前退出函数。

对这段 `quote!` 代码使用了 `Some` 进行包裹。在迭代循环的 `filter_map` 中，如果返回 `None`，迭代器将忽略该元素。这样，对于 `FieldType::Optional(_)` 类型的字段，`required_field_checks` 将不会包含任何代码。

## 07 Repeated field

在生成构建器时，对于类似 `Vec` 这样在可重复类型（迭代）也许我们想一个一个的添加元素，而非一次性添加一个 `Vec`。这就需要通过过程宏的属性（`attributes`）来实现。

### 测试用例

```rust
#[derive(Builder7)]
pub struct Command {
  executable: String,
  #[builder(each = "arg")]
  args: Vec<String>,
  #[builder(each = "env")]
  env: Vec<String>,
  current_dir: Option<String>,
}

fn main() {
  let command = Command::builder()
    .executable("cargo".to_owned())
    .arg("build".to_owned())
    .arg("--release".to_owned())
    .build()
    .unwrap();

  assert_eq!(command.executable, "cargo");
  assert_eq!(command.args, vec!["build", "--release"]);
}
```

接下来我们一步一步分析 `Builder7` 的实现。

### `Builder7`

```rust
#[proc_macro_derive(Builder7, attributes(builder))]
pub fn derive_builder7(input: TokenStream) -> TokenStream {
  let input = parse_macro_input!(input as DeriveInput);
  let expanded = builder7::expand(input).unwrap_or_else(|err| err.to_compile_error());
  expanded.into()
}
```

首先是 `Builder7` 的定义。使用 `attributes(builder)` 参数指定，允许使用 `builder` 属性来定制生成的行为。而对 `builder` 属性的使用，主要在 `BuilderField::try_from` 和 `make_build_fn` 函数中。

### `BuilderField::try_from`

```rust
fn try_from(field: &Field) -> syn::Result<Self> {
    // 初始化`each`标识符为None，用于存储重复字段的标识符。
    let mut each = None::<Ident>;

    // 遍历字段的所有属性，寻找名为`builder`的属性。
    for attr in field.attrs.iter() {
      if !attr.path().is_ident("builder") {
        continue;
      }

      // 定义错误消息：期望的属性格式
      let expected = r#"expected `builder(each = "...")`"#;

      // 解析属性的元数据，确保它是一个列表类型。
      let meta = match &attr.meta {
        Meta::List(meta) => meta,
        meta => return Err(Error::new_spanned(meta, expected)),
      };

      // 解析嵌套的元数据，寻找`each`参数。
      meta.parse_nested_meta(|nested| {
        // 如果找到`each`参数，则解析其值并更新`each`变量。
        if nested.path.is_ident("each") {
          let lit: LitStr = nested.value()?.parse()?;
          each = Some(lit.parse()?);
          Ok(())
        } else {
          // 如果遇到未知参数或未设置 `each` 参数，则返回错误。
          Err(Error::new_spanned(meta, expected))
        }
      })?; // 这里的 ? 处理 `parse_nested_meta` 调用后的错误并提前返回
    }

    let ident = field.ident.clone().unwrap();

    // 如果`each`有值，说明字段是重复类型。
    if let Some(each) = each {
      return Ok(BuilderField::new(ident, FieldType::Repeated(each, field.ty.clone())));
    }

    // ...
  }
```

`BuilderField::try_from` 函数相对之前代码更加复杂，它将处理对 `builder` 属性和 `each` 参数的解析。代码里已添加必要的注释。

在判断 `each` 参数是否存在的 `if` 语句内，我们通过 `let lit: LitStr = nested.value()?.parse()?;` 来从嵌套的元数据项中提取并解析一个字符串字面量（`LitStr`）。对于我们的测试用例，当设置的注解属性为 `#[builder(each = "arg")]` 时，`lit` 将被设置为 `"arg"`。

为了更好的理解新的派生宏 `Builder7`，我们来看看它宏展开后生成的可能的 Rust 代码：

```rust
pub struct CommandBuilder {
  executable: Option<String>,
  args: Vec<String>,
  env: Option<Vec<String>>,
  current_dir: Option<String>,
}
impl Command {
  pub fn builder() -> CommandBuilder { CommandBuilder {
    executable: Option::None,
    // 使用 Vec<String> 初始化构建器的 args 属性
    args: <Vec<String>>::new(),
    env: Option::None,
    current_dir: Option::None
  } }
}
impl CommandBuilder {
  pub fn executable(&mut self, executable: String) -> &mut Self {
    self.executable = Option::Some(executable);
    self
  }
  // 根据 `builder` 属性的 `each` 参数生成 `setter` 方法来一次添加一个值。
  pub fn arg(&mut self, arg: <Vec<String> as std::iter::IntoIterator>::Item) -> &mut Self {
    self.args.push(arg);
    self
  }
  pub fn env(&mut self, env: Vec<String>) -> &mut Self {
    self.env = Option::Some(env);
    self
  }
  pub fn current_dir(&mut self, current_dir: String) -> &mut Self {
    self.current_dir = Option::Some(current_dir);
    self
  }
  pub fn build(&mut self) -> Result<Command, Box<dyn Error>> {
    let executable = self.executable.take()
      .ok_or_else(|| { Box::<dyn Error>::from("value is not set: executable") })?;
    let env = self.env.take()
      .ok_or_else(|| { Box::<dyn Error>::from("value is not set: env") })?;
    Ok(Command {
      executable,
      args: core::mem::replace(&mut self.args, <Vec<String>>::new()),
      env,
      current_dir: self.current_dir.take()
    })
  }
}
```

在 `Command` 的关联函数 `builder` 中，对于 `args` 字段直接初始化为一个空的 `Vec<String>`。

查看生成的 `arg` 设置方法，这里根据 `Vec<String>` 实现了 `IntIterator`，先将其转换为 `IntoIterator<Item = String>`，再从中获得出 `Item` 来得到 `arg` 的正确类型为 `String`。

构建器的 `build` 函数中，去掉了对 `args` 的是否设置验证，因为 `args` 是 `Vec<T>` 字段，可以添加多个元素。

## 08 Unrecognied attribute

第 8 个测试用例验证当使用未知的属性时，返回期望的错误。

```rust
#[test]
fn tests() {
  let t = trybuild::TestCases::new();
  t.compile_fail("tests/08-unrecognized-attribute.rs");
}
```

首先在测试代码中，使用 `t.compile_fail` 函数来验证 `08-unrecognized-attribute.rs` 文件中的代码不能编译通过。且编译错误信息包含期望的错误输出，该期望错误信息由测试代码相同目录的 `08-unrecognized-attribute.stderr` 文件提供：

```rust
error: expected `builder(each = "...")`
  --> tests/08-unrecognized-attribute.rs:22:5
   |
22 |   #[builder(eac = "arg")]
   |     ^^^^^^^^^^^^^^^^^^^^
```

这个错误信息表明，在 `builder` 属性中，我们使用了一个未知的参数 `eac`，而期望的是 `each`。当我们将 `eac` 改为 `each` 或其它非 `eac` 字段，该测试用例都将失败。

## 09 Redefined prelude types

如果某些标准库 `prelude::*` 的名称在调用者的代码中含义不同，宏还能正常工作吗？考虑这种情况似乎不合理，但在实践中确实存在。最常见的是结果，板块有时会使用一个结果类型别名，该别名带有一个假定其板块错误类型的单一类型参数。这种类型别名会破坏宏生成的代码，因为宏生成的代码希望结果有两个类型参数。另一个例子是，Hyper 0.10 曾经将 `hyper::Ok` 定义为 `hyper::status::StatusCode::Ok` 的重导出，而 `hyper::status::StatusCode::Ok` 与 `Result::Ok` 完全不同。这就给 `use hyper::*` 的代码和引用 `Ok` 的宏生成代码带来了问题。

一般来说，设计供他人使用的所有宏（过程宏和规则宏）都应通过绝对路径（如 `core::result::Result`）来引用其扩展代码中的所有内容。考虑如下测试用例：

```rust
type Option = ();
type Some = ();
type None = ();
type Result = ();
type Box = ();

#[derive(Builder7)]
pub struct Command {
  executable: String,
}

fn main() {}
```

使用 `Builder7` 宏编译代码，将生成如下错误：

```shell
error[E0107]: type alias takes 0 generic arguments but 1 generic argument was supplied
  --> tests/09-redefined-prelude-types.rs:25:10
   |
25 | #[derive(Builder7)]
   |          ^^^^^^^^ expected 0 generic arguments
   |
note: type alias defined here, with 0 generic parameters
  --> tests/09-redefined-prelude-types.rs:19:6
   |
19 | type Option = ();
   |      ^^^^^^
   = note: this error originates in the derive macro `Builder7` (in Nightly builds, run with -Z macro-backtrace for more info)

error[E0107]: type alias takes 0 generic arguments but 2 generic arguments were supplied
  --> tests/09-redefined-prelude-types.rs:25:10
   |
25 | #[derive(Builder7)]
   |          ^^^^^^^^ expected 0 generic arguments
   |
note: type alias defined here, with 0 generic parameters
  --> tests/09-redefined-prelude-types.rs:22:6
   |
22 | type Result = ();
   |      ^^^^^^
   = note: this error originates in the derive macro `Builder7` (in Nightly builds, run with -Z macro-backtrace for more info)
...
```

我们可以看到，当 `Option`、`Result` 被重定义后，宏 `Builder7` 生成的代码会因为类型参数数量不匹配而报错。修复错误也非常简单，在我们的宏生成代码中（比如在 `quote!` 中）使用绝对路径来引用类型。如下所示：

- `Option<T>` -> `core::option::Option<T>`
- `Some` -> `core::option::Option::Some`
- `None` -> `core::option::Option::None`
- `Result` -> `core::result::Result`
- `Box` -> `alloc::boxed::Box`

## 小结

到此，一个可用的 `Builder` 派生宏就已经实现了，可能的进一步优化或功能增强就留给读者自行实现。

本文所涉及代码可以在 [https://github.com/yangjing/learn-rust/tree/main/proc-macro-workshop](https://github.com/yangjing/learn-rust/tree/main/proc-macro-workshop) 上查看。
