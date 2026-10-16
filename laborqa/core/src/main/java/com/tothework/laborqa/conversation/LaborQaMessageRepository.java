package com.tothework.laborqa.conversation;

import com.tothework.laborqa.config.LaborQaSchema;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface LaborQaMessageRepository extends ListCrudRepository<LaborQaMessage, Long> {

    List<LaborQaMessage> findAllBySessionIdOrderByIdAsc(Long sessionId);

    List<LaborQaMessage> findTop10BySessionIdOrderByIdDesc(Long sessionId);

    @Modifying
    @Query("DELETE FROM " + LaborQaSchema.NAME + ".labor_qa_messages WHERE session_id = :sessionId")
    void deleteAllOfSession(Long sessionId);

    @Modifying
    @Query("DELETE FROM " + LaborQaSchema.NAME + ".labor_qa_messages WHERE session_id IN ("
            + "SELECT id FROM " + LaborQaSchema.NAME + ".labor_qa_sessions WHERE user_id = :userId)")
    void deleteAllOfUser(Long userId);
}
