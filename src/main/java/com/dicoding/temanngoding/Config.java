
package com.dicoding.temanngoding;

import com.dicoding.temanngoding.dao.UserDao;
import com.dicoding.temanngoding.dao.UserDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
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
        String dbUrl="postgres://rhrkrhgkrzdagp:98650e8fe3ef7613daca4edfd9bea361dffe73e7a4198a092ea279caaa9ada50@ec2-107-20-141-145.compute-1.amazonaws.com:5432/d5fc9iok388cqb";
        String username="rhrkrhgkrzdagp";
        String password="98650e8fe3ef7613daca4edfd9bea361dffe73e7a4198a092ea279caaa9ada50";

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
    public UserDao getPersonDao()
    {
        return new UserDaoImpl(getDataSource());
    }
};
