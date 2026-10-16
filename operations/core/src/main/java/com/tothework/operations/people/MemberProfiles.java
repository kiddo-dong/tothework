package com.tothework.operations.people;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 작성자·확인자의 이름과 사진. operations는 사람을 memberId로만 기록하므로, 응답을 만들 때 store에 소속을(memberId →
 * userId), identity에 사용자를 묻는다. 목록은 두 곳에 한 번씩만 묻는다.
 */
@Component
@RequiredArgsConstructor
public class MemberProfiles {

    private final Memberships memberships;
    private final UserDirectory userDirectory;

    /** memberId → 사용자. 사용자를 찾지 못한 멤버는 빠진다. */
    public Map<Long, UserSummary> of(Long storeId, Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Membership> members = memberships.findAllByStore(storeId).stream()
                .filter(member -> memberIds.contains(member.memberId()))
                .collect(Collectors.toMap(Membership::memberId, member -> member));
        Map<Long, UserSummary> users = userDirectory.findAll(members.values().stream().map(Membership::userId).toList());
        return members.values().stream()
                .filter(member -> users.containsKey(member.userId()))
                .collect(Collectors.toMap(Membership::memberId, member -> users.get(member.userId())));
    }

    /** 방금 권한 확인으로 받은 소속의 사용자. 탈퇴 직후 등으로 없으면 null. */
    public UserSummary of(Membership membership) {
        return userDirectory.find(membership.userId()).orElse(null);
    }

    public static String nameOf(UserSummary user) {
        return user == null ? null : user.name();
    }

    public static String imageOf(UserSummary user) {
        return user == null ? null : user.profileImageUrl();
    }
}
