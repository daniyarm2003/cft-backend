package com.cft.config;

import com.cft.entities.CFTEvent;
import com.cft.entities.Fight;
import com.cft.repos.CFTEventRepo;
import com.cft.repos.FightRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ScriptConfig {

    private final Logger logger = LoggerFactory.getLogger(ScriptConfig.class);

    @Autowired
    private CFTEventRepo eventRepo;

    @Autowired
    private FightRepo fightRepo;

    private void updateFightNums() {
        List<CFTEvent> events = this.eventRepo.findAll();

        events.forEach(event -> {
            int curFightNum = 0;
            List<Fight> fights = event.getFights();

            for(Fight fight : fights) {
                fight.setFightNum(curFightNum++);
                this.fightRepo.save(fight);
            }

            event.setNextFightNum(curFightNum);
            this.eventRepo.save(event);
        });

        this.logger.info("Updated fight numbers");
    }

    @Bean
    public CommandLineRunner setEventFights() {
        return args -> {
            // this.updateFightNums();
        };
    }
}
