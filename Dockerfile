# 基础镜像
FROM eclipse-temurin:8-jdk

LABEL maintainer="zifang"

# 设置时区 + 装 wget (供 HEALTHCHECK 使用)
RUN apt-get update && apt-get install -y --no-install-recommends tzdata wget && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get remove -y tzdata && apt-get autoremove -y && apt-get clean

WORKDIR /app

COPY z-mist-admin/target/z-mist-admin-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# 健康检查（FEATURE034 T4）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O- http://127.0.0.1:8080/doc.html || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]