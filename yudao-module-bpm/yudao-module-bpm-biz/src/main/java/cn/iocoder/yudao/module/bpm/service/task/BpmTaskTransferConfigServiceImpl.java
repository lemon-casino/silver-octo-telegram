package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmTaskTransferConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class BpmTaskTransferConfigServiceImpl implements BpmTaskTransferConfigService {

    @Resource
    private BpmTaskTransferConfigMapper transferConfigMapper;

    @Override
    public Long createTaskTransferConfig(BpmTaskTransferConfigSaveReqVO createReqVO) {
        BpmTaskTransferConfigDO config = BeanUtils.toBean(createReqVO, BpmTaskTransferConfigDO.class);
        transferConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    public void updateTaskTransferConfig(BpmTaskTransferConfigSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        BpmTaskTransferConfigDO updateObj = BeanUtils.toBean(updateReqVO, BpmTaskTransferConfigDO.class);
        transferConfigMapper.updateById(updateObj);
    }

    @Override
    public void deleteTaskTransferConfig(Long id) {
        validateExists(id);
        transferConfigMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (id == null) {
            return;
        }
        if (transferConfigMapper.selectById(id) == null) {
            throw new IllegalArgumentException("task transfer config not found");
        }
    }

    @Override
    public BpmTaskTransferConfigDO getTaskTransferConfig(Long id) {
        return transferConfigMapper.selectById(id);
    }

    @Override
    public PageResult<BpmTaskTransferConfigDO> getTaskTransferConfigPage(BpmTaskTransferConfigPageReqVO pageReqVO) {
        return transferConfigMapper.selectPage(pageReqVO);
    }
}
