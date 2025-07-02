title: Greenplum 6.x 安装注意事项
date: 2020-11-19 15:06:59
category:

- data
- greenplum

tags:

- greenplum
- postgresql
- centos

---

## /etc/sysctl.conf 设置注意

```shell
 kernel.shmall=echo $(expr $(getconf _PHYS_PAGES) / 2)
 kernel.shmmax=echo $(expr $(getconf _PHYS_PAGES) / 2 \* $(getconf PAGESIZE))
```
