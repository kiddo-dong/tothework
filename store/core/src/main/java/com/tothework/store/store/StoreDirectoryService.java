package com.tothework.store.store;

import com.tothework.store.api.StoreDirectory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** store가 다른 서비스에 내주는 매장 목록 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class StoreDirectoryService implements StoreDirectory {

    private final StoreRepository storeRepository;

    @Override
    public List<Long> findAllActiveIds() {
        return storeRepository.findAllActiveIds();
    }
}
