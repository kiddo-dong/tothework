package com.example.albam.domain.storemember.service;

import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.dto.StoreMemberSummaryResponse;
import com.example.albam.domain.storemember.dto.UpdateAvailableDaysRequest;
import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import com.example.albam.global.file.ProfileImageUrls;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMemberService {

    private final StoreMemberRepository storeMemberRepository;
    private final ProfileImageUrls profileImageUrls;
    private final StoreAuthorizationService storeAuthorizationService;

    /** 멤버 전체 상세 목록 (시급·이메일·공제방식 등 민감정보 포함) — OWNER/MANAGER. */
    public List<StoreMemberResponse> getMembers(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .map(member -> StoreMemberResponse.from(member, profileImageUrls.of(member.getUser())))
                .toList();
    }

    /** 멤버 요약 목록 (이름·역할만, 재직자만) — 매장 멤버 누구나 조회 가능. */
    public List<StoreMemberSummaryResponse> getMemberSummaries(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .map(member -> StoreMemberSummaryResponse.from(member, profileImageUrls.of(member.getUser())))
                .toList();
    }

    @Transactional
    public StoreMemberResponse updateMember(Long storeId, Long memberId, Long userId,
            UpdateStoreMemberRequest request) {
        StoreMember requester = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMember(storeId, memberId);

        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자 정보는 이 API로 변경할 수 없습니다.");
        }
        rejectSelfServingEdit(requester, target, request);
        if (request.role() != null) {
            if (request.role() == MemberRole.OWNER) {
                throw new ForbiddenException("소유권 이전은 이 API로 할 수 없습니다. 소유권 이전 기능을 이용해 주세요.");
            }
            target.changeRole(request.role());
        }
        // 직함은 지울 수 있어야 하므로 빈 문자열도 받는다. null은 "안 보냄"이라 건드리지 않는다.
        if (request.title() != null) {
            target.changeTitle(request.title());
        }
        if (request.hourlyWage() != null) {
            if (request.hourlyWage() < LaborStandards.MINIMUM_HOURLY_WAGE) {
                throw new InvalidRequestException(
                        "시급은 최저임금(" + LaborStandards.MINIMUM_HOURLY_WAGE + "원) 이상이어야 합니다.");
            }
            target.changeHourlyWage(request.hourlyWage());
        }
        if (request.status() != null) {
            target.changeStatus(request.status());
        }
        if (request.availableDays() != null) {
            target.changeAvailableDays(request.availableDays());
        }
        if (request.weeklyHolidayDay() != null) {
            target.changeWeeklyHolidayDay(request.weeklyHolidayDay());
        }
        if (request.taxMode() != null) {
            target.changeTaxMode(request.taxMode());
        }
        return StoreMemberResponse.from(target, profileImageUrls.of(target.getUser()));
    }

    @Transactional
    public StoreMemberResponse updateMyAvailableDays(Long storeId, Long userId, UpdateAvailableDaysRequest request) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        member.changeAvailableDays(request.availableDays());
        return StoreMemberResponse.from(member, profileImageUrls.of(member.getUser()));
    }

    /** 본인 퇴사: 근무 이력 보존을 위해 행을 지우지 않고 INACTIVE로 전환한다. */
    @Transactional
    public void leaveStore(Long storeId, Long userId) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 매장을 나갈 수 없습니다. 매장 삭제를 이용해 주세요.");
        }
        member.resign();
    }

    /** 관리자에 의한 퇴사 처리 (soft delete). */
    @Transactional
    public void removeMember(Long storeId, Long memberId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMember(storeId, memberId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 퇴사 처리할 수 없습니다.");
        }
        target.resign();
    }

    /**
     * 관리자가 <b>자기 자신의</b> 급여·권한 관련 필드를 고치는 것을 막는다.
     *
     * <p>위의 OWNER 검사만으로는 부족하다. 매니저 자신의 행은 role이 MANAGER라 그 검사를 통과하므로,
     * 매니저가 요청 한 번으로 자기 시급을 올리거나 공제를 없앨 수 있었다. 관리자 권한은 "다른 사람을
     * 관리하라"고 준 것이지 자기 대우를 정하라고 준 것이 아니다.
     *
     * <p>직함·근무가능요일·주휴일은 금전과 권한에 영향이 없어 본인도 바꿀 수 있게 남겨둔다.
     */
    private void rejectSelfServingEdit(StoreMember requester, StoreMember target,
            UpdateStoreMemberRequest request) {
        if (!requester.getId().equals(target.getId())) {
            return;
        }
        if (request.hourlyWage() != null || request.taxMode() != null) {
            throw new ForbiddenException("본인의 시급과 공제 방식은 변경할 수 없습니다. 매장 소유자에게 요청해 주세요.");
        }
        if (request.role() != null) {
            throw new ForbiddenException("본인의 역할은 변경할 수 없습니다.");
        }
        if (request.status() != null) {
            throw new ForbiddenException("본인의 재직 상태는 이 API로 변경할 수 없습니다. 매장 나가기를 이용해 주세요.");
        }
    }

    private StoreMember getStoreMember(Long storeId, Long memberId) {
        StoreMember target = storeMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        return target;
    }
}
