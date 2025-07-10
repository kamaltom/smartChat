package com.fourp.smartChat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application {

    public static void main(String[] args) throws Exception {
        // Start Spring context
        var context = SpringApplication.run(Application.class, args);

        //seedFAQs(context);
        //seedFeatures(context);
        
    
    }
    
    private static void seedFAQs(ConfigurableApplicationContext context) throws Exception{
        FaqSeeder seeder = context.getBean(FaqSeeder.class);
        System.out.println("📄 Seeding ");
        seeder.seedFAQs();
        System.out.println("✅ FAQ seeding complete.");
    }

    private static void seedFeatures(ConfigurableApplicationContext context) throws Exception{
        FaqSeeder seeder = context.getBean(FaqSeeder.class);
        System.out.println("📄 Seeding features");
        seeder.seedFeatures();
        System.out.println("✅ Features seeding complete.");
    }
}
