package com.youmo.core.service;

import com.youmo.common.entity.WorldSetting;
import java.util.Optional;

public interface WorldSettingService {

    Optional<WorldSetting> getByBookId(Long bookId);

    WorldSetting saveOrUpdate(Long bookId, WorldSetting setting);
}
