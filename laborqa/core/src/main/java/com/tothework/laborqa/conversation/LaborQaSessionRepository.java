package com.tothework.laborqa.conversation;

import com.tothework.laborqa.config.LaborQaSchema;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface LaborQaSessionRepository extends ListCrudRepository<LaborQaSession, Long> {

    List<LaborQaSession> findAllByUserIdOrderByIdDesc(Long userId);

    @Modifying
    @Query("DELETE FROM " + LaborQaSchema.NAME + ".labor_qa_sessions WHERE user_id = :userId")
    void deleteAllOfUser(Long userId);
}
