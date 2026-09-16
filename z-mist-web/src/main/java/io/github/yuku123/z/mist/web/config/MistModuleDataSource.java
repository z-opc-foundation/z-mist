package io.github.yuku123.z.mist.web.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zifang.z.boot.datasource.starter.ModuleDataSourceTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "io.github.yuku123.z.mist.core.domain.mapper", sqlSessionFactoryRef = "sqlSessionFactoryMist")
public class MistModuleDataSource extends ModuleDataSourceTemplate {

    @Bean("dataSourceMist")
    public DataSource dataSource(org.springframework.core.env.Environment env) {
        return buildDataSource(env, "mist");
    }

    @Bean("sqlSessionFactoryMist")
    public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSourceMist) throws Exception {
        return buildSqlSessionFactory(dataSourceMist);
    }
}
