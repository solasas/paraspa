package com.sashank.paraspa.config;

import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private SkillRepository skillRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (skillRepository.count() > 0) return;

        skillRepository.saveAll(List.of(
            skill("Guitar",           SkillCategory.MUSIC,    "Learn to play acoustic or electric guitar"),
            skill("Piano",            SkillCategory.MUSIC,    "Piano lessons for all levels"),
            skill("Singing",          SkillCategory.MUSIC,    "Vocal training and singing techniques"),
            skill("Drums",            SkillCategory.MUSIC,    "Drum lessons for beginners and beyond"),
            skill("Java",             SkillCategory.TECH,     "Java programming language"),
            skill("Python",           SkillCategory.TECH,     "Python programming language"),
            skill("Web Development",  SkillCategory.TECH,     "HTML, CSS, JavaScript web development"),
            skill("React",            SkillCategory.TECH,     "React front-end framework"),
            skill("Cooking Italian",  SkillCategory.COOKING,  "Learn to cook authentic Italian dishes"),
            skill("Baking",           SkillCategory.COOKING,  "Baking cakes, bread, and pastries"),
            skill("Sushi Making",     SkillCategory.COOKING,  "Japanese sushi and roll preparation"),
            skill("Spanish",          SkillCategory.LANGUAGE, "Spanish language lessons"),
            skill("French",           SkillCategory.LANGUAGE, "French language lessons"),
            skill("Japanese",         SkillCategory.LANGUAGE, "Japanese language lessons"),
            skill("German",           SkillCategory.LANGUAGE, "German language lessons"),
            skill("Yoga",             SkillCategory.FITNESS,  "Yoga practice and flexibility training"),
            skill("Weight Training",  SkillCategory.FITNESS,  "Strength and resistance training"),
            skill("Running",          SkillCategory.FITNESS,  "Running technique and training plans"),
            skill("Drawing",          SkillCategory.ART,      "Basic drawing and sketching techniques"),
            skill("Painting",         SkillCategory.ART,      "Watercolor and acrylic painting"),
            skill("Photography",      SkillCategory.ART,      "Photography composition and editing"),
            skill("Public Speaking",  SkillCategory.OTHER,    "Presentation and communication skills"),
            skill("Chess",            SkillCategory.OTHER,    "Chess strategy for all levels")
        ));
    }

    private Skill skill(String name, SkillCategory category, String description) {
        return Skill.builder()
                .name(name)
                .category(category)
                .description(description)
                .build();
    }
}
