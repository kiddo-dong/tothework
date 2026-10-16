package com.tothework.operations.notice;

import com.tothework.identity.api.UserSummary;
import com.tothework.operations.api.Notices;
import com.tothework.operations.people.MemberProfiles;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService implements Notices {

    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final StoreAccess storeAccess;
    private final Memberships memberships;
    private final MemberProfiles memberProfiles;

    @Transactional
    public NoticeResponse createNotice(Long storeId, Long userId, CreateNoticeRequest request) {
        Membership author = storeAccess.requireOwnerOrManager(storeId, userId);
        Notice notice = noticeRepository.save(Notice.write(storeId, author.memberId(), request.title(),
                request.content()));
        return toResponse(notice, memberProfiles.of(author), 0, false);
    }

    /** 공지 목록 (내 확인 여부 + 확인한 인원수 포함). 공지·확인 기록·작성자를 각각 한 번에 읽는다. */
    public List<NoticeResponse> getNotices(Long storeId, Long userId) {
        Membership me = storeAccess.requireMember(storeId, userId);
        List<Notice> notices = noticeRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId);
        if (notices.isEmpty()) {
            return List.of();
        }
        List<NoticeRead> reads = noticeReadRepository.findAllByNoticeIdIn(notices.stream().map(Notice::getId).toList());
        Map<Long, Long> readCountByNoticeId = reads.stream()
                .collect(Collectors.groupingBy(NoticeRead::getNoticeId, Collectors.counting()));
        Set<Long> readByMe = reads.stream()
                .filter(read -> read.getMemberId().equals(me.memberId()))
                .map(NoticeRead::getNoticeId)
                .collect(Collectors.toSet());
        Map<Long, UserSummary> authors = memberProfiles.of(storeId,
                notices.stream().map(Notice::getAuthorId).distinct().toList());
        return notices.stream()
                .map(notice -> toResponse(notice, authors.get(notice.getAuthorId()),
                        readCountByNoticeId.getOrDefault(notice.getId(), 0L), readByMe.contains(notice.getId())))
                .toList();
    }

    /** "확인했습니다" 버튼. 이미 확인한 공지에 다시 눌러도 오류 없이 넘어간다 (멱등). */
    @Transactional
    public void markRead(Long storeId, Long noticeId, Long userId) {
        Membership me = storeAccess.requireMember(storeId, userId);
        Notice notice = getNoticeInStore(storeId, noticeId);
        noticeReadRepository.insertIfAbsent(notice.getId(), me.memberId(), LocalDateTime.now());
    }

    /** 공지별 확인 현황: 재직 멤버 전원의 확인 여부·시각 (관리자용). */
    public List<NoticeReadStatusResponse> getReadStatus(Long storeId, Long noticeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Notice notice = getNoticeInStore(storeId, noticeId);
        Map<Long, LocalDateTime> readAtByMemberId = noticeReadRepository.findAllByNoticeId(notice.getId()).stream()
                .collect(Collectors.toMap(NoticeRead::getMemberId, NoticeRead::getReadAt));
        List<Membership> active = memberships.findAllByStore(storeId).stream().filter(Membership::isActive).toList();
        Map<Long, UserSummary> people = memberProfiles.of(storeId, active.stream().map(Membership::memberId).toList());
        return active.stream()
                .map(member -> new NoticeReadStatusResponse(member.memberId(),
                        MemberProfiles.nameOf(people.get(member.memberId())),
                        MemberProfiles.imageOf(people.get(member.memberId())),
                        readAtByMemberId.get(member.memberId())))
                .toList();
    }

    /** 확인 기록은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Transactional
    public void deleteNotice(Long storeId, Long noticeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        noticeRepository.delete(getNoticeInStore(storeId, noticeId));
    }

    @Override
    public long countUnread(Long storeId, Long memberId) {
        return noticeReadRepository.countUnread(storeId, memberId);
    }

    private Notice getNoticeInStore(Long storeId, Long noticeId) {
        return noticeRepository.findByIdAndStoreId(noticeId, storeId)
                .orElseThrow(() -> new NotFoundException("공지를 찾을 수 없습니다."));
    }

    private static NoticeResponse toResponse(Notice notice, UserSummary author, long readCount, boolean readByMe) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(), MemberProfiles.nameOf(author),
                MemberProfiles.imageOf(author), notice.getCreatedAt(), readCount, readByMe);
    }
}
