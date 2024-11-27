title: Akka启用ssl和HTTP 2
date: 2019-06-18 21:40:14
category:
  - scala
  - akka
tags:
  - akka
  - akka-http
  - ssl
  - https
  - http-2
---

Akka原生支持SSL、HTTPS、HTTP 2，本文记录下各SSL的使用配置。

**X.509证书**

公钥证书是解决身份问题的一种方法。若仅加密是可以建立一个安全的连接，但不能保证你正在与你认为正在与之通信的服务器通信。如果没有某种方法来验证远程服务器的身份，攻击者仍然可以将自己作为远程服务器，然后将安全连接转发到伪造的远程服务器。公钥证书就是用来解决这个问题的。

考虑公共密钥证书的最佳方法是使用护照系统。证书用于以一种难以伪造的方式建立有关信息持有人的信息。这就是为什么证书验证如此重要：接受任何证书意味着即使是攻击者的证书也将被盲目接受。

## 生成 X.509 证书

本文示例使用Java 1.8的`keytool`来创建证书。

### 生成随机密码

通过`pwgen`命令生成10位字符的随机密码到`password`文件，这个密码文件将在接下来各步骤中使用。

```
pwgen -Bs 10 1 > password
```

*Linux：`sudo apt install pwgen`或`sudo yum install pwgen`，Mac：`brew install pwgen`*

### 服务器配置

你将需要一个分配了DNS主机名的服务器来验证主机名。在这个例子中，我们假设主机名是`yangjing.dev`。

### 创建自签名证书

#### 生成服务端SSL证书

第一步是创建将对`yangjing.me`证书进行签名的证书颁发机构。根CA证书有几个附加属性（`ca:true`，`keyCertSign`），这些属性明确地将其标记为CA证书，并将保存在信任存储中。

```bash
#!/bin/sh

export PW=`cat password`

KEY_FILE=ssl-key

# 创建自签名密钥CA证书和私钥
keytool -genkeypair -v \
  -dname "CN=www.yangjing.me, OU=Yangbajing, O=Yangbajing, L=Beijing, ST=Beijing, C=CN" \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -ext SAN="DNS:www.yangjing.me,DNS:yangjing.me,DNS:about.yangjing.me" \
  -validity 9999 \
  -alias ${KEY_FILE} \
  -keystore ${KEY_FILE}.jks \
  -keypass:env PW \
  -storepass:env PW

# 以下命令查看刚生成的证书密钥内容：
# keytool -keystore ${KEY_FILE}.jks -list -v

# 导出${KEY_FILE}公共证书上为${KEY_FILE}.crt，以便在信任存储中使用
keytool -export -v \
  -alias ${KEY_FILE} \
  -file ${KEY_FILE}.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore ${KEY_FILE}.jks \
  -rfc
```

## 生成 yangjing.dev 证书

### SSL证书配置

```bash
#!/bin/sh
# 生成 ${DOMAIN} 证书，${DOMAIN} 证书由 ${DOMAIN} 服务器在握手时提供

LANG=en_US.UTF-8
export PW=`cat password`
export DOMAIN="yangjing.dev"
export KEY_FILE=ssl-key

# 创建绑定到 ${DOMAIN} 的服务器证书
keytool -genkeypair -v \
  -alias ${DOMAIN} \
  -dname "CN=${DOMAIN}, OU=Yangbajing, O=Yangbajing, L=Beijing, ST=Beijing, C=CN" \
  -keystore ${DOMAIN}.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385
#keytool -importkeystore -srckeystore localdev.com.jks -destkeystore localdev.com.jks -deststoretype pkcs12

# 为 ${DOMAIN} 创建证书签名请求
# 从技术上讲，密码使用DHE或ECDHE数字签名，RSA进行密码加密。
# 在你创建完自签名证书和密钥后,还需要前进一小步来创建证书签名申请 (certificate signingrequest,CSR):
# 现在可以将文件 ${DOMAIN}.csr 提交给你的CA用于签发正式证书了。
keytool -certreq -v \
  -alias ${DOMAIN} \
  -keypass:env PW \
  -storepass:env PW \
  -keystore ${DOMAIN}.jks \
  -file ${DOMAIN}.csr

# 告诉 ${KEY_FILE} 签署 ${DOMAIN} 证书。注意，扩展是根据请求而不是原始证书。
# csr -> crt
keytool -gencert -v \
  -alias ${KEY_FILE} \
  -keypass:env PW \
  -storepass:env PW \
  -keystore ${KEY_FILE}.jks \
  -infile ${DOMAIN}.csr \
  -outfile ${DOMAIN}.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:${DOMAIN}" \
  -rfc

# 告诉 ${DOMAIN}.jks 可以信任 ${KEY_FILE} 作为签名者
keytool -import -v \
  -alias ${KEY_FILE} \
  -file ${KEY_FILE}.crt \
  -keystore ${DOMAIN}.jks \
  -storetype JKS \
  -storepass:env PW << EOF
yes
EOF

# 将签名导入到 ${DOMAIN}.jks
keytool -import -v \
  -alias ${DOMAIN} \
  -file ${DOMAIN}.crt \
  -keystore ${DOMAIN}.jks \
  -storetype JKS \
  -storepass:env PW
```

可以列出`yangjing.jks`的内容以确认。若使用`Play`（等Java应用），这将存储到服务器的密钥存储区。

```
keytool -list -v -keystore yangjing.jks -storepass:env PW
```

将可看到类似如下输出：
```
Keystore type: jks
Keystore provider: SUN

Your keystore contains 1 entry

Alias name: yangjing.dev
Creation date: Jun 18, 2019
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: CN=yangjing.dev, OU=Yangbajing, O=Yangbajing, L=Beijing, ST=Beijing, C=CN
Issuer: CN=yangjing.dev, OU=Yangbajing, O=Yangbajing, L=Beijing, ST=Beijing, C=CN
....
```

## 导出Nginx可用的PEM

如果 yangjing.me 不使用Java作为TLS端点，同时你想使用Nginx。可需要导出 **PEM** 格式的证书。这需要 `openssl` 来导出私钥。

```bash
#!/bin/sh

export PW=`cat password`
DOMAIN=hongkazhijia.dev

# 导出 ${DOMAIN} 公钥供 Nginx 使用
keytool -export -v \
  -alias ${DOMAIN} \
  -file ${DOMAIN}.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore ${DOMAIN}.jks \
  -rfc

# 创建包含公钥和私钥的 PKCS12 密钥库
keytool -importkeystore -v \
  -srcalias ${DOMAIN} \
  -srckeystore ${DOMAIN}.jks \
  -srcstoretype jks \
  -srcstorepass:env PW \
  -destkeystore ${DOMAIN}.p12 \
  -destkeypass:env PW \
  -deststorepass:env PW \
  -deststoretype PKCS12

# 导出 ${DOMAIN} 私钥以在 Nginx 中使用。注意，这需要使用 openssl
openssl pkcs12 \
  -nocerts \
  -nodes \
  -in ${DOMAIN}.p12 \
  -out ${DOMAIN}.key \
  -passout env:PW \
  -passin env:PW

# 清理
rm ${DOMAIN}.p12
```

生成的 `yangjing.dev.crt`（公钥）和 `yangjing.dev.key`（私钥）两个文件。作为示例，可以这样配置Nginx使用。

```
ssl_certificate      /etc/nginx/certs/yangjing.dev.crt;
ssl_certificate_key  /etc/nginx/certs/yangjing.dev.key;
```

如果使用的是客户端身份验证，还需要添加：
```
ssl_client_certificate /etc/nginx/certs/clientca.crt;
ssl_verify_client on;
```

可以通过如下命令来检查服务器的证书（需要先启动 yangjing.dev HTTP服务器）：

```
keytool -printcert -sslserver yangjing.dev
```

## HTTP 2

TODO

## 小结

TODO
