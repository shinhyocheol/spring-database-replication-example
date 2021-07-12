package com.replication.app.config.database;

import java.util.LinkedHashMap;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.replication.app.config.database.datasource.ReplicationRoutingDataSource;
import com.replication.app.config.database.properties.DatabaseProperties;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@EnableJpaAuditing
@Configuration
@PropertySources(
	@PropertySource(value = "classpath:application.yml", ignoreResourceNotFound = true)
)
@AllArgsConstructor
@Slf4j
public class DatabaseConfig {

	private DatabaseProperties databaseProperty;

	private JpaProperties jpaProperties;

	public DataSource createDataSource(String url) {

		SimpleDriverDataSource dataSource = new SimpleDriverDataSource();

		dataSource.setUrl(url);
		dataSource.setDriverClass(com.mysql.cj.jdbc.Driver.class);
		dataSource.setUsername(databaseProperty.getUsername());
		dataSource.setPassword(databaseProperty.getPassword());

		return dataSource;
	}

	@Bean
	public DataSource routingDataSource() {

		ReplicationRoutingDataSource replicationRoutingDataSource = new ReplicationRoutingDataSource();

		DataSource master = createDataSource(databaseProperty.getUrl());

		Map<Object, Object> dataSourceMap = new LinkedHashMap<>();
		dataSourceMap.put("master", master);

		databaseProperty.getSlaveList().forEach(slave -> {
			dataSourceMap.put(slave.getName(), createDataSource(slave.getUrl()));
		});

		replicationRoutingDataSource.setTargetDataSources(dataSourceMap);
		replicationRoutingDataSource.setDefaultTargetDataSource(master);

		return replicationRoutingDataSource;
	}

	@Bean
	@DependsOn({"routingDataSource"})
	public DataSource dataSource() {
		return new LazyConnectionDataSourceProxy(routingDataSource());
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		
		LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
		entityManagerFactoryBean.setDataSource(dataSource());
		entityManagerFactoryBean.setPackagesToScan("com.replication.app");
		
		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		
		entityManagerFactoryBean.setJpaVendorAdapter(vendorAdapter);
		entityManagerFactoryBean.setJpaPropertyMap(jpaProperties.getProperties());

		return entityManagerFactoryBean;
	}
	
	@Bean
	public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager tm = new JpaTransactionManager();
		tm.setEntityManagerFactory(entityManagerFactory);
		return tm;
	}

}
