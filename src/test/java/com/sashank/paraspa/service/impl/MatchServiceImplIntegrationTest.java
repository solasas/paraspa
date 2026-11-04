package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.support.CountingDataSourceConfig;
import com.sashank.paraspa.support.StatementCounter;
import com.sashank.paraspa.util.DtoMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real matching queries on an in-memory database. Proves two things about the batched implementation:
 * it sends the same number of SQL statements however many users exist (the old one sent two per user), and it
 * returns exactly what the old per-user algorithm returned.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import({MatchServiceImpl.class, DtoMapper.class, CountingDataSourceConfig.class})
class MatchServiceImplIntegrationTest {

    @Autowired private MatchServiceImpl matchService;
    @Autowired private EntityManager em;

    private final StatementCounter counter = CountingDataSourceConfig.COUNTER;

    // ------------------------------------------------------------------ helpers

    private User user(String name) {
        User u = User.builder().name(name).email(name + "@example.com").password("x").build();
        em.persist(u);
        return u;
    }

    private List<Skill> skills(int count) {
        return skills("skill", count);
    }

    private List<Skill> skills(String prefix, int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            Skill s = Skill.builder().name(prefix + i).category(SkillCategory.TECH).build();
            em.persist(s);
            return s;
        }).toList();
    }

    private void add(User user, Skill skill, SkillType type) {
        em.persist(UserSkill.builder().user(user).skill(skill).type(type).level(SkillLevel.BEGINNER).build());
    }

    private void freshRequest() {
        em.flush();
        em.clear();
        counter.reset();
    }

    /** The old algorithm, written the obvious way over the whole table: for every other user, intersect. */
    private List<String> bruteForce(User me) {
        List<UserSkill> all = em.createQuery("select us from UserSkill us join fetch us.user join fetch us.skill", UserSkill.class).getResultList();
        Set<Long> myLearn = all.stream().filter(us -> us.getUser().getId().equals(me.getId()) && us.getType() == SkillType.LEARN)
                .map(us -> us.getSkill().getId()).collect(Collectors.toSet());
        Set<Long> myTeach = all.stream().filter(us -> us.getUser().getId().equals(me.getId()) && us.getType() == SkillType.TEACH)
                .map(us -> us.getSkill().getId()).collect(Collectors.toSet());
        List<Long> otherIds = all.stream().map(us -> us.getUser().getId()).filter(id -> !id.equals(me.getId()))
                .distinct().sorted().toList();

        List<String> rows = new ArrayList<>();
        for (Long otherId : otherIds) {
            List<Long> theyTeachMe = all.stream()
                    .filter(us -> us.getUser().getId().equals(otherId) && us.getType() == SkillType.TEACH && myLearn.contains(us.getSkill().getId()))
                    .map(us -> us.getSkill().getId()).sorted().toList();
            List<Long> iTeachThem = all.stream()
                    .filter(us -> us.getUser().getId().equals(otherId) && us.getType() == SkillType.LEARN && myTeach.contains(us.getSkill().getId()))
                    .map(us -> us.getSkill().getId()).sorted().toList();
            if (!theyTeachMe.isEmpty() || !iTeachThem.isEmpty()) {
                rows.add(describe(otherId, theyTeachMe, iTeachThem, !theyTeachMe.isEmpty() && !iTeachThem.isEmpty()));
            }
        }
        // mutual first, otherwise ascending user id (the order the service documents)
        rows.sort(Comparator.comparing((String r) -> r.endsWith("mutual=true") ? 0 : 1)
                .thenComparing(r -> Long.parseLong(r.substring(0, r.indexOf('|')))));
        return rows;
    }

    private static String describe(Long userId, List<Long> theyTeachMe, List<Long> iTeachThem, boolean mutual) {
        return userId + "|theyTeachMe=" + theyTeachMe + "|iTeachThem=" + iTeachThem + "|mutual=" + mutual;
    }

    private static List<String> describe(List<MatchResponse> matches) {
        return matches.stream().map(m -> describe(m.getUser().getId(),
                m.getTheyCanTeachMe().stream().map(s -> s.getId()).sorted().toList(),
                m.getICanTeachThem().stream().map(s -> s.getId()).sorted().toList(),
                m.isMutual())).toList();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void findMatches_returnsExactlyTheComplementaryUsers() {
        List<Skill> s = skills(4);   // 0 guitar, 1 piano, 2 pottery, 3 drums
        User me = user("me");
        User mutual = user("mutual");
        User teacherOnly = user("teacherOnly");
        User learnerOnly = user("learnerOnly");
        User unrelated = user("unrelated");
        add(me, s.get(0), SkillType.TEACH);      // I teach guitar
        add(me, s.get(1), SkillType.LEARN);      // I want piano
        add(mutual, s.get(1), SkillType.TEACH);        // teaches piano
        add(mutual, s.get(0), SkillType.LEARN);        // wants guitar
        add(teacherOnly, s.get(1), SkillType.TEACH);   // teaches piano only
        add(learnerOnly, s.get(0), SkillType.LEARN);   // wants guitar only
        add(unrelated, s.get(2), SkillType.TEACH);
        add(unrelated, s.get(3), SkillType.LEARN);
        freshRequest();

        List<MatchResponse> matches = matchService.findMatches(me.getId());

        assertThat(matches).extracting(m -> m.getUser().getId())
                .containsExactly(mutual.getId(), teacherOnly.getId(), learnerOnly.getId());   // mutual first, then by id
        assertThat(matches).extracting(MatchResponse::isMutual).containsExactly(true, false, false);
        assertThat(matches).allSatisfy(m -> assertThat(m.getUser().getEmail()).isNull());      // matches never carry the email
    }

    @Test
    void findMatches_sendsTheSameNumberOfStatementsWhetherThereAreTenUsersOrTwoHundred() {
        List<Skill> s = skills(6);
        User me = user("me");
        add(me, s.get(0), SkillType.TEACH);
        add(me, s.get(1), SkillType.LEARN);

        List<Integer> statementCounts = new ArrayList<>();
        int created = 0;
        for (int population : new int[]{10, 50, 200}) {
            for (; created < population; created++) {
                User u = user("person" + created);
                add(u, s.get(created % 6), created % 2 == 0 ? SkillType.TEACH : SkillType.LEARN);
                add(u, s.get((created + 1) % 6), SkillType.LEARN);
            }
            freshRequest();
            List<MatchResponse> matches = matchService.findMatches(me.getId());
            statementCounts.add(counter.count());
            assertThat(matches).isNotEmpty();
        }

        // user lookup (1) + my learn list (1) + my teach list (1) + people who teach what I want (1)
        // + people who want what I teach (1) = 5, for 10 users and for 200 users. The old code needed 2 per user.
        assertThat(statementCounts).as("statements for 10, 50 and 200 users").containsOnly(5);
    }

    @Test
    void findMatches_neverLoadsUsersOneAtATime() {
        List<Skill> s = skills(3);
        User me = user("me");
        add(me, s.get(0), SkillType.LEARN);
        add(me, s.get(1), SkillType.TEACH);
        for (int i = 0; i < 40; i++) {
            User u = user("p" + i);
            add(u, s.get(0), SkillType.TEACH);
            add(u, s.get(1), SkillType.LEARN);
        }
        freshRequest();

        matchService.findMatches(me.getId());

        List<String> sql = counter.statements().stream().map(String::toLowerCase).toList();
        assertThat(sql).hasSize(5);
        // the two batched lookups bring the user along (join users); every list of skill rows brings its skill along
        assertThat(sql.stream().filter(q -> q.contains("join users")).count()).isEqualTo(2);
        assertThat(sql.stream().filter(q -> q.contains("join skills")).count()).isEqualTo(4);
        assertThat(sql.stream().filter(q -> q.contains("from users") && !q.contains("join")).count())
                .as("only the single lookup of the current user").isEqualTo(1);
    }

    @Test
    void findMatches_returnsNothing_andRunsNoBatchedQuery_forAUserWithNoSkills() {
        skills(2);
        User me = user("me");
        user("other");
        freshRequest();

        assertThat(matchService.findMatches(me.getId())).isEmpty();

        assertThat(counter.count()).isEqualTo(3);   // user lookup + the two (empty) own lists; no IN () queries
    }

    @Test
    void findMatches_giveTheSameAnswerAsTheBruteForceAlgorithm_onRandomData() {
        for (long seed : new long[]{1, 2, 3, 42, 2026}) {
            em.clear();
            Random random = new Random(seed);
            List<Skill> s = skills("seed" + seed + "skill", 12);
            List<User> people = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                User u = user("s" + seed + "u" + i);
                people.add(u);
                Set<String> used = new HashSet<>();
                int rows = random.nextInt(6);          // some users have no skills at all
                for (int r = 0; r < rows; r++) {
                    int skill = random.nextInt(12);
                    SkillType type = random.nextBoolean() ? SkillType.TEACH : SkillType.LEARN;
                    if (used.add(skill + ":" + type)) {   // (user, skill, type) is unique in the schema
                        add(u, s.get(skill), type);
                    }
                }
            }
            em.flush();

            for (User me : people.subList(0, 12)) {
                em.clear();
                assertThat(describe(matchService.findMatches(me.getId())))
                        .as("seed %d, user %s", seed, me.getName())
                        .containsExactlyElementsOf(bruteForce(me));
            }
        }
    }
}
