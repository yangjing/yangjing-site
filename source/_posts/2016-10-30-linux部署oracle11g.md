title: Linux 部署 Oracle11G
date: 2016-10-30 22:40:09
categories: bigdata
tags:

- linux
- oracle
- neokylin

---

## 安装 Oracle 数据库

本文基于 RHEL6/Centos6/Neokylin6，其它发行版请注意区别。

**安装依赖软件包**

```
sudo yum install binutils compat-libcap1 compat-libstdc++-33 compat-libstdc++-33.i686 gcc-c++ glibc glibc.i686 glibc-devel glibc-devel.i686 ksh libgcc libgcc.i686 libstdc++ libstdc++.i686 libstdc++-devel libstdc++-devel.i686 libaio libaio.i686 libaio-devel libaio-devel.i686 make sysstat unixODBC unixODBC.i686 unixODBC-devel unixODBC-devel.i686 elfutils-libelf-devel mksh
```

**创建操作系统用户和组**

1. 查看`/etc/oraInst.loc`文件是否存在，不存在则创建：

```
# more /etc/oraInst.loc
```

不存在则创建文件并保存如下内容：

```
inventory_loc=/opt/app/oraInventory
inst_group=oinstall
```

2. 创建用户组

```
sudo groupadd oinstall
sudo groupadd dba
```

3. 创建用户

```
sudo useradd -g oinstall -G dba oracle
```

用户存在则修改用户：

```
sudo usermod -g oinstall -G dba oracle
```

## 配置内核参数

编辑`/etc/sysct.conf`文件，添加或修改如下配置：

```
fs.aio-max-nr = 1048576
fs.file-max = 6815744
kernel.shmall = 2097152
kernel.shmmax = 536870912
kernel.shmmni = 4096
kernel.sem = 250 32000 100 128
net.ipv4.ip_local_port_range = 9000 65500
net.core.rmem_default = 262144
net.core.rmem_max = 4194304
net.core.wmem_default = 262144
net.core.wmem_max = 1048576
```

编辑`/etc/security/limits.conf`文件，添加或修改如下配置：

```
*      soft      nofile        4996
*      hard      nofile        65536
*      soft      stack         10240
*      hard      stack         32768
*      soft      nproc         2047
*      hard      nproc         16384
```

## 创建需要的目录

```
sudo mkdir -p /opt/app/oracle
sudo chown -R oracle:oinstall /opt/app/
sudo chmod -R 775 /opt/app/
```

## 设置用户环境变量

切换到`oracle`用户，编辑`~/.bash_profile`文件添加如下配置荐：

```
export ORACLE_BASE="/opt/app/oracle"
export ORACLE_UNQNAME=DB11G
export ORACLE_SID=DB11G
export ORACLE_HOME="$ORACLE_BASE/product/11.2.0/dbhome_1"
export LD_LIBRARY_PATH="$ORACLE_HOME/lib:$LD_LIBRARY_PATH"
export PATH="$ORACLE_HOME/bin:$PATH:$HOME/bin"
```

# 安装数据库

为了使之前的配置生效，**需要重启操作系统**。使用**oracle**账号登录系统，并执行`runInstaller`命令安装 Oracle11G 数据库系统。

```
./runInstaller
```

**安装时注意事项**

1. 使用静态 IP 安装 Oracle11G

2. 若安装时报虚拟内存不足，可以挂一个文件做为虚拟内存：

创建一个 1G 大小的空白文件：

```
sudo if=/dev/zero bs=/opt/swapfile bs=1024k count=1024
```

创建 swap 文件：

```
sudo /sbin/mkswap swapfile
```

挂载 swap 文件：

```
sudo swapon swapfile
```

3. 本文介绍的 Oracle 数据库安装需要 Linux 图形界面支持。

4. 在执行安装数据库步骤：17/20 检查 依赖项时提示某些程序包未找到，其实这里相应报已经安装。可以使用`rpm -qa | grep <package name>`命令查看，在确认已安装后可以**全部忽略**。

# 使用 Oracle11G

## 手动启动 Oracle 数据库

**启动数据库**

```
sqlplus / as sysdba
> start
```

**启动网络监听**

```
lsnrctl start
```

**启动管理控制台**

```
emctl start dbconsole
```

## 用户管理

**创建用户**

```
create user 用户名 identified by 密码;
alter user 用户名 account unlock;
```

**授权**

TODO

## 资源限制

查看**resource_limit**参数：

```sql
> show parameter resource_limit
```

若为 FALSE，则设置资源限制参数为 TRUE：

```sql
alter system set resource_limit = TRUE;
```

该改变对密码资源无效，密码资源总是可用的

创建 PROFILE：

```sql
> create profile user_session_limit limit sessions_per_user 5; --最大连接数限制为5
```

将 PROFILE 指定给用户：

```sql
> alter user ydgwb profile sess;
```
