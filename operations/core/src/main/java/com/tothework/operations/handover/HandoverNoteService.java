package com.tothework.operations.handover;

import com.tothework.identity.api.UserSummary;
import com.tothework.operations.api.HandoverNoteResponse;
import com.tothework.operations.api.HandoverNotes;
import com.tothework.operations.people.MemberProfiles;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.shared.web.DateRanges;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverNoteService implements HandoverNotes {

    private final HandoverNoteRepository handoverNoteRepository;
    private final StoreAccess storeAccess;
    private final MemberProfiles memberProfiles;

    @Transactional
    public HandoverNoteResponse createNote(Long storeId, Long userId, CreateHandoverNoteRequest request) {
        Membership author = storeAccess.requireMember(storeId, userId);
        LocalDate workDate = request.workDate() == null ? LocalDate.now() : request.workDate();
        HandoverNote note = handoverNoteRepository.save(HandoverNote.write(storeId, author.memberId(),
                request.content(), workDate));
        return toResponse(note, memberProfiles.of(author));
    }

    public List<HandoverNoteResponse> getNotes(Long storeId, Long userId, LocalDate from, LocalDate to) {
        DateRanges.requireValid(from, to);
        storeAccess.requireMember(storeId, userId);
        return withAuthors(storeId,
                handoverNoteRepository.findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(storeId, from, to));
    }

    @Override
    public List<HandoverNoteResponse> findRecent(Long storeId, LocalDate from, LocalDate to, int limit) {
        return withAuthors(storeId, handoverNoteRepository.findRecent(storeId, from, to, limit));
    }

    /** 수정은 삭제와 같은 권한 — 작성자 본인 또는 관리자만. */
    @Transactional
    public HandoverNoteResponse updateNote(Long storeId, Long noteId, Long userId,
            CreateHandoverNoteRequest request) {
        HandoverNote note = requireEditableNote(storeId, noteId, userId, "수정");
        note.update(request.content(), request.workDate() == null ? note.getWorkDate() : request.workDate());
        HandoverNote saved = handoverNoteRepository.save(note);
        return withAuthors(storeId, List.of(saved)).getFirst();
    }

    /** 삭제는 작성자 본인 또는 관리자만. */
    @Transactional
    public void deleteNote(Long storeId, Long noteId, Long userId) {
        handoverNoteRepository.delete(requireEditableNote(storeId, noteId, userId, "삭제"));
    }

    private HandoverNote requireEditableNote(Long storeId, Long noteId, Long userId, String action) {
        Membership me = storeAccess.requireMember(storeId, userId);
        HandoverNote note = handoverNoteRepository.findByIdAndStoreId(noteId, storeId)
                .orElseThrow(() -> new NotFoundException("인수인계 노트를 찾을 수 없습니다."));
        if (!note.getAuthorId().equals(me.memberId()) && !me.isOwnerOrManager()) {
            throw new ForbiddenException("작성자 본인 또는 매장 관리자만 " + action + "할 수 있습니다.");
        }
        return note;
    }

    private List<HandoverNoteResponse> withAuthors(Long storeId, List<HandoverNote> notes) {
        Map<Long, UserSummary> authors = memberProfiles.of(storeId,
                notes.stream().map(HandoverNote::getAuthorId).distinct().toList());
        return notes.stream().map(note -> toResponse(note, authors.get(note.getAuthorId()))).toList();
    }

    private static HandoverNoteResponse toResponse(HandoverNote note, UserSummary author) {
        return new HandoverNoteResponse(note.getId(), note.getAuthorId(), MemberProfiles.nameOf(author),
                MemberProfiles.imageOf(author), note.getContent(), note.getWorkDate(), note.getCreatedAt());
    }
}
