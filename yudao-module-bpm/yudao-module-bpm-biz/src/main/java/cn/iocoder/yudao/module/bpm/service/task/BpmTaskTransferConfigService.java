package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;

import jakarta.validation.Valid;

public interface BpmTaskTransferConfigService {

    Long createTaskTransferConfig(@Valid BpmTaskTransferConfigSaveReqVO createReqVO);

    void updateTaskTransferConfig(@Valid BpmTaskTransferConfigSaveReqVO updateReqVO);

    void deleteTaskTransferConfig(Long id);

    BpmTaskTransferConfigDO getTaskTransferConfig(Long id);

    PageResult<BpmTaskTransferConfigDO> getTaskTransferConfigPage(BpmTaskTransferConfigPageReqVO pageReqVO);
  
    BpmTaskTransferConfigDO getActiveTaskTransferConfig(Long fromUserId, String modelId, Integer modelVersion);

    void putTaskTransferConfigrevoke(Long id);
}
