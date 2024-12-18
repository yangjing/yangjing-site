title: Rust 过程宏：实现 Builder（一）
date: 2024-12-17 16:01:50
category: rust
tags: [rust, macros, proc-macro, syn, quote]

---

[syn](https://github.com/dtolnay/syn) 、[quote](https://github.com/dtolnay/quote) 和 [proc-macro2](https://github.com/dtolnay/proc-macro2) 的作者提供了一个很好的 Rust 过程宏学习教程： [Rust Latam：过程宏工作坊](https://github.com/dtolnay/proc-macro-workshop) 。本文本基于 [`derive(Builder)`](https://github.com/dtolnay/proc-macro-workshop/blob/master/README.md#derive-macro-derivebuilder) 项目，补充了完整的学习过程和代码。

## 学习之前

本系列文章会涵盖属性宏、派生宏和类似函数的过程宏。

请注意，系列文章的内容将假设你对 `struct`、`enum`、`trait`、`impl trait`、`泛型参数` 和 `trait 边界` 有一定的理解。若你是 Rust 语言的初学者，也许你需要先阅读并学习一些入门教程：

- [《Rust 程序设计语言》](https://kaisery.github.io/trpl-zh-cn/)
- [《通过例子学 Rust》](https://rustwiki.org/zh-CN/rust-by-example/)
- [《Rust 宏小册》](https://zjp-cn.github.io/tlborm/)

## 测试用例

```rust
#[test]
fn tests() {
  let t = trybuild::TestCases::new();
  t.pass("tests/01-parse.rs");
  t.pass("tests/02-create-builder.rs");
  t.pass("tests/03-call-setters.rs");
  t.pass("tests/04-call-build.rs");
  t.pass("tests/05-method-chaining.rs");
  t.pass("tests/06-optional-field.rs");
  t.pass("tests/07-repeated-field.rs");
  t.compile_fail("tests/08-unrecognized-attribute.rs");
  t.pass("tests/09-redefined-prelude-types.rs");
}
```

一共有 9 个测试用例，使我们可以逐步实现一个 `Builder` 派生宏。

测试代码使用了 [trybuild](https://crates.io/crates/trybuild) crate，可用于在一组测试用例上调用 rustc，并断言任何生成的错误消息都是预期的。

此类测试通常用于测试涉及过程宏的错误报告。我们将编写测试用例，触发宏检测到的错误或在生成的扩展代码中由 Rust 编译器检测到的错误，并与预期错误进行比较，以确保它们保持用户友好。

这种测试风格有时被称为用户界面测试，因为它们测试用户与库的交互方面，这些方面超出了普通 API 测试所涵盖的内容。

## 01 Parse

第一个测试用例非常简单，它只测试派生宏是否可以成功解析。测试代码如下：

```rust
#[derive(Builder)]
pub struct Command {
  executable: String,
  args: Vec<String>,
  env: Vec<String>,
  current_dir: Option<String>,
}

fn main() {}
```

对应的派生宏实现：

```rust
#[proc_macro_derive(Builder)]
pub fn derive_builder(input: TokenStream) -> TokenStream {
  let input = parse_macro_input!(input as DeriveInput);
  let expanded = expand(input).unwrap_or_else(|err| err.to_compile_error());
  expanded.into()
}

pub fn expand(input: DeriveInput) -> syn::Result<TokenStream2> {
  // 获取结构体的可见性和标识符
  let vis = &input.vis;
  let input_ident = &input.ident;

  // 生成构建器结构体的标识符
  let builder_ident = Ident::new(&format!("{}Builder", input_ident), Span::call_site());

  // 使用quote宏生成构建器结构体的定义，并将其包装在Ok中返回
  Ok(quote! {
    #vis struct #builder_ident {
    }
  })
}
```

首先是定义过程宏。通过 `#[proc_macro_derive(Builder)]` 注解声明这是一个过程宏，并且指定了需要处理的 `derive` 类型为 `Builder`。`parse_macro_input!` 宏用于解析输入的令牌流参数（`TokenStream`）并将其转换为 `DeriveInput` 类型，它由 `syn` crate 提供。然后再调用 `expand` 帮助函数来根据输入的派生信息生成一个构建器结构体的定义。

`expand` 函数接收一个 `DeriveInput` 类型的 `input` 参数，其中包含了被派生结构体的可见性、标识符和数据结构等信息。它基于这些信息生成一个对应构建器结构体的定义，并返回生成的 [`TokenStream2`](https://docs.rs/proc-macro2/latest/proc_macro2/struct.TokenStream.html) 作为输出，包含生成的构建器模式代码流或错误信息。

## 02 Create Builder

### 派生宏生成的代码

首先来看看效果，在使用 `#[derive(Builder)]` 派生宏后生成的代码展开类似如下：

```rust
pub struct CommandBuilder {
  executable: Option<String>,
  args: Option<Vec<String>>,
  env: Option<Vec<String>>,
  current_dir: Option<String>,
}
impl Command {
  pub fn builder() -> CommandBuilder {
    CommandBuilder {
      executable: None,
      args: None,
      env: None,
      current_dir: None
    }
  }
}
```

宏定义部分和 [示例 01](#01-parse) 一样，主要的不同在 `expand` 函数。

```rust
pub fn expand(input: DeriveInput) -> syn::Result<TokenStream2> {
  let vis = &input.vis;
  let input_ident = &input.ident;
  let builder_ident = Ident::new(&format!("{}Builder", input_ident), Span::call_site());

  let fields = extract_struct_fields(input.data)?;

  // 将字段信息转换为构建器字段信息
  let builder_fields: Vec<BuilderField> =
    fields.named.iter().map(BuilderField::try_from).collect::<syn::Result<_>>()?;

  let storage = make_storage(&builder_fields);
  let initializer = make_initializer(&builder_fields);

  Ok(quote! {
    #vis struct #builder_ident {
      #storage // 生成构建器结构体的字段存储代码
    }

    impl #input_ident {
      #vis fn builder() -> #builder_ident {
        // 实例化一个 `XxxxBuilder` 构建器并初始化
        #builder_ident {
          #initializer // 生成构建器结构体的初始化代码
        }
      }
    }
  })
}
```

上面代码多了 3 个函数和一个数据类型。下面来分别进行解读。

### `extract_struct_fields`

```rust
pub fn extract_struct_fields(data: Data) -> syn::Result<FieldsNamed> {
  // 根据数据类型进行匹配，我们只关心结构体类型的数据
  match data {
    // 当数据是结构体类型时，进一步检查其字段类型
    Data::Struct(s) => match s.fields {
      // 如果字段是命名字段，直接返回这些字段
      Fields::Named(fields) => Ok(fields),
      // 如果字段类型不是命名字段，返回错误，指明期望的是命名字段
      fields => return Err(Error::new_spanned(fields, "expected named fields")),
    },
    // 当数据类型不是结构体时，返回错误，指明期望的是结构体
    _ => return Err(Error::new(Span::call_site(), "expected struct")),
  }
}
```

此函数旨在处理特定的 AST（抽象语法树）节点，具体来说是处理结构体定义。它尝试从提供的数据中提取出结构体的命名字段。如果数据不满足预期格式，函数将返回一个错误。

### `BuilderField`

```rust
enum FieldType {
  Plain(Type), // 普通字段类型，直接包含字段的类型信息
  Optional(Type),
}

struct BuilderField {
  ident: Ident, // 字段标识符，用于在代码中引用该字段
  ty: FieldType, // 字段类型
}
```

`FieldType` 定义字段类型枚举，用于描述字段是否可选或必填。`BuilderField` 是构建器字段结构体，用于描述构建器中的字段信息。

### `make_storage`

```rust
fn make_storage(fields: &[BuilderField]) -> TokenStream2 {
  fields.iter().map(|field| {
    let ident = &field.ident;
    // 根据字段类型生成存储结构
    let storage = match &field.ty {
      FieldType::Plain(ty) => quote!(Option<#ty>),
      FieldType::Optional(ty) => quote!(#ty),
    };
    quote! { // 生成字段的定义代码
      #ident: #storage,
    }
  })
  .collect() // 将所有字段的定义代码合并成一个TokenStream
}
```

`make_storage` 函数接受一个 `BuilderField` 类型的切片作为输入，遍历每个字段，并根据字段的类型生成相应的存储结构。对于普通类型字段，生成一个该类型的 `Option`；对于 `Optional` 类型字段，直接使用其类型。

### `make_initializer`

```rust
fn make_initializer(fields: &[BuilderField]) -> TokenStream2 {
  fields.iter().map(|field| {
    let ident = &field.ident;
    quote! {
      #ident: None, // 为每个字段生成初始化为None的代码
    }
  })
  .collect()
}
```

`make_initializer` 函数比较简单，根据提供的字段信息生成初始化器代码。

## 03 Call setter

第 3 个示例是实现构建器的`setter`方法。其 `expand` 函数与 [02 Create Builder](#02-create-builder) 不同的地方在：

```rust
pub fn expand(input: DeriveInput) -> syn::Result<TokenStream2> {
  // ...
  let setters = make_setters(vis, &builder_fields);
  // ...

  Ok(quote! {
    // ...
    impl #builder_ident {
      #setters
    }
  })
}
```

### `make_setters`

```rust
fn make_setters(vis: &Visibility, fields: &[BuilderField]) -> TokenStream2 {
  fields.iter().map(|field| {
    let ident = &field.ident;
    // 根据字段类型获取其内部类型，无论是普通类型还是可选类型
    let ident_type = match &field.ty {
      FieldType::Plain(ty) => ty,
      FieldType::Optional(ty) => ty,
    };
    // 生成并返回设置器方法的Token流
    quote! {
      #vis fn #ident(&mut self, #ident: #ident_type) -> &mut Self {
        self.#ident = Some(#ident);
        self
      }
    }
  })
  .collect()
}
```

此函数遍历每个字段信息，根据字段的类型生成对应的设置器方法。对于普通类型和可选类型字段，生成的方法会将字段设置为 `Some(value)`，并返回 `&mut Self` 以支持链式调用。`vis: &Visibility` 参数指定设置器方法的可见性修饰符，如 `pub` 或默认可见性（[Visibility::Inherited](https://docs.rs/syn/latest/syn/enum.Visibility.html) 一种继承的可见性，通常意味着私有）。

### 派生宏生成的 `CommandBuilder` 构建器 `impl`

在使用 `#[derive(Builder)]` 派生宏后生成的 `CommandBuilder` 代码展开类似如下：

```rust
impl CommandBuilder {
  pub fn executable(&mut self, executable: String) -> &mut Self {
    self.executable = Some(executable);
    self
  }
  pub fn args(&mut self, args: Vec<String>) -> &mut Self {
    self.args = Some(args);
    self
  }
  pub fn env(&mut self, env: Vec<String>) -> &mut Self {
    self.env = Some(env);
    self
  }
  pub fn current_dir(&mut self, current_dir: String) -> &mut Self {
    self.current_dir = Some(current_dir);
    self
  }
}
```

## 04 Call `build`

接下来为 `XxxxBuilder` 实现 `build` 方法，返回 `Xxxx` 结构体。先看看使用代码：

```rust
fn main() {
  let mut builder = Command::builder();
  builder.executable("cargo".to_owned());
  builder.args(vec!["build".to_owned(), "--release".to_owned()]);
  builder.env(vec![]);
  builder.current_dir("..".to_owned());

  let command = builder.build().unwrap(); // 这里调用 build 生成 Command 对象
  assert_eq!(command.executable, "cargo");
}
```

在 `expand` 函数中，添加 `build_fn` 变量并生成对应的 `build` 方法。

```rust
pub fn expand(input: DeriveInput) -> syn::Result<TokenStream2> {
  // ...
  let build_fn = make_build_fn(vis, input_ident, &builder_fields);

  Ok(quote! {
    // ...
    impl #builder_ident {
      #setters
      #build_fn // 生成 build 方法
    }
  })
}
```

### `make_build_fn`

```rust
fn make_build_fn(vis: &Visibility, input_ident: &Ident, fields: &[BuilderField]) -> TokenStream2 {
  // 遍历所有字段，为每个非可选字段生成检查代码，确保它们在构建之前已被设置
  let required_field_checks = fields.iter().filter_map(|field| {
    let ident = &field.ident;
    match &field.ty {
      FieldType::Plain(_) => Some(quote! {
        let #ident = self.#ident.ok_or_else(|| {
          Box::<dyn Error>::from(format!("value is not set: {}", stringify!(#ident)))
        })?;
      }),
      FieldType::Optional(_) => None,
    }
  });

  // 遍历所有字段，生成字段赋值代码，将构建器中的字段值转移到新构建的结构体实例中
  let field_assignment = fields.iter().map(|field| {
    let ident = &field.ident;
    let expr = match &field.ty {
      FieldType::Plain(_) => quote!(#ident),
      FieldType::Optional(_) => quote!(self.#ident.take()),
    };
    quote! {
      #ident: #expr,
    }
  });

  // 生成并返回完整的构建函数代码
  quote! {
    #vis fn build(self) -> Result<#input_ident, Box<dyn Error>> {
      #(#required_field_checks)*

      Ok(#input_ident {
        #(#field_assignment)*
      })
    }
  }
}
```

`make_build_fn` 函数负责根据输入的结构体字段信息生成一个构建函数，该函数将检查所有必需的字段是否已设置值，并将所有字段（包括可选字段）从构建器实例中提取出来，最终构建并返回一个结构体实例。

### 派生宏生成的 `build` 方法

在使用 `#[derive(Builder)]` 派生宏后生成的 `build` 构建方法代码展开类似如下：

```rust
impl CommandBuilder {
  // ...
  pub fn build(mut self) -> Result<Command, Box<dyn Error>> {
    let executable = self.executable.ok_or_else(|| {
      Box::<dyn Error>::from(format!("value is not set: {}", stringify!(executable)))
    })?;
    let args = self.args.ok_or_else(|| {
      Box::<dyn Error>::from(format!("value is not set: {}", stringify!(args)))
    })?;
    let env = self.env.ok_or_else(|| {
      Box::<dyn Error>::from(format!("value is not set: {}", stringify!(env)))
    })?;
    Ok(Command { executable, args, env, current_dir: self.current_dir })
  }
}
```

从生成的代码中可以看到，`executable`、`args` 和 `env` 3 个字段都实现了 `Option` 类型。所以当它们没有被设置时通过调用 `ok_or_else` 来设置错误信息并返回一个 `Result`，然后通过 `?` 操作符来在字段未设置时提前终止 `build` 函数并返回相应的错误信息。

## 小结

到此，一个可用的 `Builder` 派生宏就实现了。但它还可以继续完善，我们将在下一篇文章中继续讨论剩下的几个测试用例。
