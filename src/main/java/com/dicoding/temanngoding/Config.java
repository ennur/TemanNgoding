
package com.dicoding.temanngoding;

import com.dicoding.temanngoding.dao.UserDao;
import com.dicoding.temanngoding.dao.UserDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:application.properties")
public class Config
{
    @Autowired
    Environment mEnv;

    @Bean
    public DataSource getDataSource()
    {
        String dbUrl=System.getenv("JDBC_DATABASE_URL");
        String username=System.getenv("JDBC_DATABASE_USERNAME");
        String password=System.getenv("JDBC_DATABASE_PASSWORD");

        DriverManagerDataSource ds=new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername(username);
        ds.setPassword(password);

        return ds;
    }
    
    @Bean(name="com.linecorp.channel_secret")
    public String getChannelSecret()
    {
        return mEnv.getProperty("com.linecorp.channel_secret");
    }
    
    @Bean(name="com.linecorp.channel_access_token")
    public String getChannelAccessToken()
    {
        return mEnv.getProperty("com.linecorp.channel_access_token");
    }

    @Bean
    public UserDao getUserDao()
    {
        return new UserDaoImpl(getDataSource());
    }
}
