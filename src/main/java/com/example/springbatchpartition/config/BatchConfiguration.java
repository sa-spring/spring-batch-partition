package com.example.springbatchpartition.config;

import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

    Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    @Autowired
    JobBuilderFactory jobBuilderFactory;

    @Autowired
    StepBuilderFactory stepBuilderFactory;

    @Bean
    public Job partitioningJob() throws Exception {
        return jobBuilderFactory.get("partitioningJob").incrementer(new RunIdIncrementer()).flow(masterStep()).end()
                .build();
    }

    @Bean
    public Step masterStep() throws Exception {
        return stepBuilderFactory.get("masterStep").partitioner(slaveStep()).partitioner("partition", partitioner())
                .gridSize(10).taskExecutor(new SimpleAsyncTaskExecutor()).build();
    }

    @Bean
    public Partitioner partitioner() throws Exception {
        MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        partitioner.setResources(resolver.getResources("file://persona*"));
        return partitioner;
    }

    @Bean
    public Step slaveStep() throws Exception {
        return stepBuilderFactory.get("slaveStep").<Map<String, String>, Map<String, String>>chunk(1)
                .reader(reader(null)).writer(writer()).build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Map<String, String>> reader(@Value("#{stepExecutionContext['fileName']}") String file)
            throws MalformedURLException {
        FlatFileItemReader<Map<String, String>> reader = new FlatFileItemReader<>();
        reader.setResource(new UrlResource(file));

        DefaultLineMapper<Map<String, String>> lineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(":");
        tokenizer.setNames("key", "value");

        lineMapper.setFieldSetMapper((fieldSet) -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put(fieldSet.readString("key"), fieldSet.readString("value"));
            return map;
        });
        lineMapper.setLineTokenizer(tokenizer);
        reader.setLineMapper(lineMapper);

        return reader;
    }

    @Bean
    public ItemWriter<Map<String, String>> writer() {
        return (items) -> items.forEach(item -> {
            item.entrySet().forEach(entry -> {
                logger.info("key->[" + entry.getKey() + "] Value ->[" + entry.getValue() + "]");
            });
        });
    }

}
