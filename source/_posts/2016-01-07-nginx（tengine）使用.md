title: Nginx（Tengine）使用
date: 2016-01-07 11:37:16
categories: work
tags:
- nginx
- tengine
---

用了一段时间Tengine了，主要用于静态资源、后端服务的反向代理、负载均衡方面。也有了一些使用经验，现在将一些配置及心得记录于此。

## Tengine的安装

Tengine的安装非常简单，就是：

```
$ ./configure
$ make
$ sudo make install
```

官方有更详细的说明：[http://tengine.taobao.org/document_cn/install_cn.html](http://tengine.taobao.org/document_cn/install_cn.html)。
我们现在的使用是使用默认编译指令编译的，以后看需要可能会对其进行一些定制化。

## 基本配置

- work_processes: Nginx的work进程，一般设为CPU核数
- events.worker_connections: 设置每个worker可服务的最大连接数
- events.reuse_port: 设置端口可重用，支持SO_REUSEPORT套接字参数，打开此特性后可明显提高系统响应能力（Linux从内核3.9开始支持）

## 日志

Nginx的日志主要分两类：access和error，从名称就可以看出是访问日志和错误日志。这里分别给出两种日志的示例。

**访问日志**

```
xxx.xxx.xxx.xxx - - [07/Jan/2016:13:39:36 +0800] "GET /api/jobui?companyName=%E4%B8%B4%E5%AE%89%E5%B0%9A%E5%BA%90%E6%96%87%E5%8C%96%E5%88%9B%E6%84%8F%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8 HTTP/1.1" "404" 69 "-" "python-requests/2.9.0" "-" "-" 0.005 239 273
xx.xxx.xx.xx - - [07/Jan/2016:13:39:37 +0800] "GET /api/news?companyName=%E4%B8%B4%E5%AE%89%E5%B0%9A%E5%BA%90%E6%96%87%E5%8C%96%E5%88%9B%E6%84%8F%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8 HTTP/1.1" "200" 70323 "-" "AHC/1.0" "-" "-" 0.066 70547 223
```

日志格式log_format设置为：

```
log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                                    '"$status" $body_bytes_sent "$http_referer" '
                                    '"$http_user_agent" "$http_x_forwarded_for" '
                                    '"$gzip_ratio" $request_time $bytes_sent $request_length';
```

- $remote_addr: 请求的远程地址
- $remote_user: 远程客户端用户名称
- $time_local: 用户访问的时间与时区
- $request: 请求HTTP方法、URL与HTTP协议
- $status: 响应状态
- $body_types_sent: 响应主体内容大小（字节）
- $http_refere: 请求从哪个页面链接过来
- $http_user_agent: 客户端用户User-Agent
- $http_x_forwarded_for: 若HTTP头信息中有X-Forwarded-For记录客户端的真实IP，这时Nginx日志就可以使用其记录真实IP（一般用于反向代理中）
- $gzip_ratio: gzip压缩率？
- $request_time: 请求处理时间，单位为秒，精度毫秒；从读入客户端的第一个字节开始，直到把最后一个字符发送给客户端后进行日志写入为止
- $bytes_sent: 发送给客户端的总字节数
- $request_length: 请求的长度（包括请求行，请求头和请求正文）

**错误日志**

```
2016/01/07 13:15:48 [error] 2874#0: check time out with peer: 10.51.xx.xx:7100
2016/01/07 13:15:53 [error] 2874#0: check time out with peer: 10.51.xx.xx:7100
```

错误日志显示Nginx运行或启动时的一此错误情况，示例是做负载均衡时检测节点健康状态失败时的错误消息。

## 代理

用Nginx做HTTP反向代理是一个很好的解决方案，代理配置一般放在 `location` 段。一个示例配置如下：

```
location / {
    proxy_pass http://scnginx001;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Host $http_host;
}
```

- proxy_pass: 指定将HTTP请求代理到哪个后端地址
- proxy_http_version: 指定代理使用的HTTP协议版本
- proxy_set_header: 设置一系列代理HTTP header

其中需要关注的是 `proxy_http_version` 和 `proxy_set_header Connection ""` 两条指令，它确定了代理将使用HTTP 1.1的keep-alive功能，
这样可以显著提高Nginx与后端被代理服务之间的响应，因为它减少了两者建立连接的次数。

## 负载均衡与健康检查（Tengine）

（注：之前写了一篇介绍 Nginx 官方的健康检查方案文章：[Nginx负载均衡与反向代理](http://www.yangjing.me/2015/11/20/nginx%E8%B4%9F%E8%BD%BD%E5%9D%87%E8%A1%A1%E4%B8%8E%E5%8F%8D%E5%90%91%E4%BB%A3%E7%90%86/)）

因为Nginx官方的健康检查是一个收费方案，所以这里的设置是基于 **Tengine** 提供的方案。先给出配置，再细说各指令的

```
upstream scnginx001 {
    #dynamic_resolve fallback=stale fail_timeout=30s;
    server xx.xxx.xxx.216:7100;
    server xx.xxx.xx.147:7100;

    check interval=3000 rise=2 fall=5 timeout=1000 type=http;
    check_keepalive_requests 100;
    check_http_send "GET /api/health_check HTTP/1.1\r\nConnection: keep-alive\r\nHost: scnginx-001\r\n\r\n";
    check_http_expect_alive http_2xx http_3xx;
}
```

首先，使用 `upstream` 要指定一个名字，这里指定为 **scnginx001** ，在配置 `proxy_pass` 时需要使用这个名字来告诉代理使用哪个 **upstream** 配置。

- server: 每个后端节点一个配置， `proxy_pass` 指令将把请求代理到这些节点上
- check: 设置健康查检参数，这里为间隔3秒做一次检查，连续失败2次认为后端服务器状态为**down**，连续成功5次认为后端服务器状态为**up**，对后端健康请求的超时时间为1秒，检查类型为 **http**。
- check_keepalive_requests: 设置 Tengine 完成几次请求后关闭连接，默认值为1。对于连接频繁的服务，适当提高此值可以显著提高性能。
- check_http_send: 配置 HTTP 健康健查包发送的内容。
- check_http_expect_alive: 指定 HTTP 回复成功状态，默认为 2XX 和 3XX 的状态认为后端服务状态是健康的。

## Tengine状态

Tengine自带了两个服务状态查检器，配置后可以通过HTTP实时看到Tengine和健康检查服务的运行状态。配置如下：

```
location /nginx_status {
    stub_status on;
    access_log off;
    allow xxx.xxx.xxx.xxx;
    deny all;
}

location /status {
    check_status;
    access_log off;
    allow xxx.xxx.xxx.xxx;
    deny all;
}
```

- stub_status: 显示Tengine服务状态
- check_status: 显示每个后端服务的健康检查服务状态

