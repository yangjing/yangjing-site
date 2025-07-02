title: 使用 HanLP 增强 Elasticsearch 分词功能
date: 2017-04-12 15:17:12
categories:

- data
- elasticsearch

tags:

- hanlp
- elasticsearch

---

_hanlp-ext 插件源码地址：https://github.com/hualongdata/hanlp-ext_

**Elasticsearch** 默认对中文分词是按“字”进行分词的，这是肯定不能达到我们进行分词搜索的要求的。官方有一个 **SmartCN** 中文分词插件，另外还有一个 IK 分词插件使用也比较广。但这里，我们采用 **HanLP** 这款 **自然语言处理工具** 来进行中文分词。

## Elasticsearch

Elasticsearch 的默认分词效果是惨不忍睹的。

```curl
    GET /_analyze?pretty
    {
      "text" : ["重庆华龙网海数科技有限公司"]
    }
```

_输出：_

```json
{
  "tokens": [
    {
      "token": "重",
      "start_offset": 0,
      "end_offset": 1,
      "type": "<IDEOGRAPHIC>",
      "position": 0
    },
    {
      "token": "庆",
      "start_offset": 1,
      "end_offset": 2,
      "type": "<IDEOGRAPHIC>",
      "position": 1
    },
    {
      "token": "华",
      "start_offset": 2,
      "end_offset": 3,
      "type": "<IDEOGRAPHIC>",
      "position": 2
    },
    {
      "token": "龙",
      "start_offset": 3,
      "end_offset": 4,
      "type": "<IDEOGRAPHIC>",
      "position": 3
    },
    {
      "token": "网",
      "start_offset": 4,
      "end_offset": 5,
      "type": "<IDEOGRAPHIC>",
      "position": 4
    },
    {
      "token": "海",
      "start_offset": 5,
      "end_offset": 6,
      "type": "<IDEOGRAPHIC>",
      "position": 5
    },
    {
      "token": "数",
      "start_offset": 6,
      "end_offset": 7,
      "type": "<IDEOGRAPHIC>",
      "position": 6
    },
    {
      "token": "科",
      "start_offset": 7,
      "end_offset": 8,
      "type": "<IDEOGRAPHIC>",
      "position": 7
    },
    {
      "token": "技",
      "start_offset": 8,
      "end_offset": 9,
      "type": "<IDEOGRAPHIC>",
      "position": 8
    },
    {
      "token": "有",
      "start_offset": 9,
      "end_offset": 10,
      "type": "<IDEOGRAPHIC>",
      "position": 9
    },
    {
      "token": "限",
      "start_offset": 10,
      "end_offset": 11,
      "type": "<IDEOGRAPHIC>",
      "position": 10
    },
    {
      "token": "公",
      "start_offset": 11,
      "end_offset": 12,
      "type": "<IDEOGRAPHIC>",
      "position": 11
    },
    {
      "token": "司",
      "start_offset": 12,
      "end_offset": 13,
      "type": "<IDEOGRAPHIC>",
      "position": 12
    }
  ]
}
```

可以看到，默认是按字进行分词的。

## elasticsearch-hanlp

**HanLP**

<a taget="_blank" href="https://github.com/hankcs/HanLP">HanLP</a> 是一款使用 Java 实现的优秀的，具有如下功能：

- 中文分词
- 词性标注
- 命名实体识别
- 关键词提取
- 自动摘要
- 短语提取
- 拼音转换
- 简繁转换
- 文本推荐
- 依存句法分析
- 语料库工具

安装 **elasticsearch-hanlp**（安装见：`https://github.com/hualongdata/hanlp-ext/tree/master/es-plugin`）插件以后，我们再来看看分词效果。

```curl
    GET /_analyze?pretty
    {
      "analyzer" : "hanlp",
      "text" : ["重庆华龙网海数科技有限公司"]
    }
```

_输出：_

```json
{
  "tokens": [
    {
      "token": "重庆",
      "start_offset": 0,
      "end_offset": 2,
      "type": "ns",
      "position": 0
    },
    {
      "token": "华龙网",
      "start_offset": 2,
      "end_offset": 5,
      "type": "nr",
      "position": 1
    },
    {
      "token": "海数",
      "start_offset": 5,
      "end_offset": 7,
      "type": "nr",
      "position": 2
    },
    {
      "token": "科技",
      "start_offset": 7,
      "end_offset": 9,
      "type": "n",
      "position": 3
    },
    {
      "token": "有限公司",
      "start_offset": 9,
      "end_offset": 13,
      "type": "nis",
      "position": 4
    }
  ]
}
```

HanLP 的功能不止简单的中文分词，有很多功能都可以集成到 Elasticsearch 中。

心动不如行动：`https://github.com/hualongdata/hanlp-ext`。
