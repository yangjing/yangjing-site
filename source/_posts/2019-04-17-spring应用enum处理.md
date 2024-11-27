title: Spring应用enum处理
date: 2019-04-17 19:10:12
category:
  - java
tags:
  - spring
  - jackson
  - mybatis-plus
  - mybatis
  - enum
---

*原文地址：[https://www.yangjing.me/2019/04/17/spring应用enum处理/](https://www.yangjing.me/2019/04/17/spring%E5%BA%94%E7%94%A8enum%E5%A4%84%E7%90%86/)*

在Spring应用开发中，Java枚举（enum）默认都是使用字符串进行序列化/反序列化的。都通常我们都想将其序列化/反序列化为int值。

## MyBatis

MyBatis-plus提供了插件用于自定义enum的序列化/反序列化，非常方便。只需要在`application.properties`配置文件中指定默认的枚举处理器即可，配置如下：

```
mybatis-plus.configuration.default-enum-type-handler: com.baomidou.mybatisplus.extension.handlers.EnumTypeHandler
```

枚举类需要实现IEnum<T>接口或将字段标记`@EnumValue`注解，这样MyBatis-plus在遇到相关枚举类型时就会通过指定的配置来序列/反序列化。

## Jackson

### 序列化

Jackson的配置要相对复杂一点，Jackson对序列化提供了默认的支持，在要使用的字段上加`JsonValue`注解即可：

```java
public enum UserStatusEnum implements IEnum<Integer> {
    DISABLE(0, "禁用"),
    NORMAL(1, "正常"),
    PLAIN(999, "普通");

    @JsonValue
    private Integer value;

    private String name;

    UserStatusEnum(Integer value, String name) {
        this.value = value;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Integer getValue() {
        return value;
    }
}
```

### 反序列化

从int反序列化到enum，需要自定义`IEnumDeserializer`反序列化器，代码如下：

```java
public class EnumDeserializers extends Deserializers.Base {
    @Override
    public JsonDeserializer<?> findEnumDeserializer(Class<?> type, DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException {
        if (IEnum.class.isAssignableFrom(type)) {
            return new IEnumDeserializer(EnumResolver.constructUnsafe(type, config.getAnnotationIntrospector()));
        }
        return super.findEnumDeserializer(type, config, beanDesc);
    }
}

public class IEnumDeserializer extends StdScalarDeserializer<IEnum<Integer>> implements ContextualDeserializer {
    private final IEnum<Integer>[] enums;
    private final EnumResolver enumResolver;

    public IEnumDeserializer(EnumResolver byNameResolver) {
        super(byNameResolver.getEnumClass());
        this.enumResolver = byNameResolver;
        this.enums = (IEnum<Integer>[]) enumResolver.getRawEnums();
    }

    @Override
    public IEnum<Integer> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        int value = p.getIntValue();

        return Arrays.stream(this.enums).filter(e -> e.getValue() == value).findFirst()
                .orElseThrow(() -> new JsonParseException(p, "枚举需要为整数类型"));
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        return this;
    }

}
```

并实现Jackson Module：

```java
public class MyModule extends Module {
    @Override
    public String getModuleName() {
        return "MyModule";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addDeserializers(new EnumDeserializers());
    }
}
```

反序列化器和Jackson Module定义好后就需要把它加入Jackson里了，有两种方式：

#### 手动注册：

获取`objectMapper`，将`MyModule`注册到Jackson。

`objectMapper.registerModule(new MyModule());`

#### 自动注册：

通过Java自带的`ServiceLoader`服务提供商加载机制来自动注册模块到Jackson。在`resources`资源目录创建服务配置文件，文件路径如下：

```
resources
  META-INF.services
    com.fasterxml.jackson.databind.Module
```

服务配置文件内容：
```
com.fasterxml.jackson.module.yangjing.MyModule
```

**自定义Spring Boot Jackson**

反序列化器、Jackson模块都写好了，我们需要自定义Spring Boot来启动Jackson的模块自动注册功能：

```java

@EnableWebFlux
@Configuration
public class WebConfiguration implements WebFluxConfigurer {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        ServerCodecConfigurer.ServerDefaultCodecs defaultCodecs = configurer.defaultCodecs();
        defaultCodecs.jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
        defaultCodecs.jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
    }

    @Bean
    @Order(-1)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false).build().findAndRegisterModules();
    }
}
```

`findAndRegisterModules()`方法将通过`ServiceLoader`机制从classpath路径中找到所有的`Module`并注册到Jackson。

在`void configureHttpMessageCodecs(ServerCodecConfigurer configurer)`函数中自定义`ServerDefaultCodecs`，使用新的`ObjectMapper`来注册`jackson2JsonEncoder`和`jackson2JsonDecoder`。

## 小结

Java提供了丰富而完善的enum机制，但大部化序列化/反序列化工具都使用**文字**来对其进行序列化/反序列化。而通常我们都会枚举序列化/反序列化为int。

Spring还是一个优秀的框架的，从公司/组织层面选择它不会错。

可以在 [https://github.com/yangjing/spring-reactive-sample](https://github.com/yangjing/spring-reactive-sample) 找到示例代码。

