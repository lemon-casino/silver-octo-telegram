package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmTaskTransferConfigMapper;
import cn.iocoder.yudao.module.bpm.enums.transfer.BpmTaskTransferStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

@Service
@Validated
public class BpmTaskTransferConfigServiceImpl implements BpmTaskTransferConfigService {

    @Resource
    private BpmTaskTransferConfigMapper transferConfigMapper;

    @Override
    public Long createTaskTransferConfig(BpmTaskTransferConfigSaveReqVO createReqVO) {
        BpmTaskTransferConfigDO config = BeanUtils.toBean(createReqVO, BpmTaskTransferConfigDO.class);
        config.setStatus(calculateStatus(createReqVO.getStartTime(), createReqVO.getEndTime()));
        transferConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    public void updateTaskTransferConfig(BpmTaskTransferConfigSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        BpmTaskTransferConfigDO updateObj = BeanUtils.toBean(updateReqVO, BpmTaskTransferConfigDO.class);
        updateObj.setStatus(calculateStatus(updateReqVO.getStartTime(), updateReqVO.getEndTime()));
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
        BpmTaskTransferConfigDO config = transferConfigMapper.selectById(id);
        if (config == null) {
            return null;
        }
        updateStatusIfNeeded(config);
        return config;
    }

    @Override
    public PageResult<BpmTaskTransferConfigDO> getTaskTransferConfigPage(BpmTaskTransferConfigPageReqVO pageReqVO) {
        PageResult<BpmTaskTransferConfigDO> page = transferConfigMapper.selectPage(pageReqVO);
        page.getList().forEach(this::updateStatusIfNeeded);
        return page;
    }
    private Integer calculateStatus(Long startTime, Long endTime) {
        long now = System.currentTimeMillis();
        if (now < startTime) {
            return BpmTaskTransferStatusEnum.WAIT.getStatus();
        }
        if (now > endTime) {
            return BpmTaskTransferStatusEnum.EXPIRED.getStatus();
        }
        return BpmTaskTransferStatusEnum.RUNNING.getStatus();
    }


    private void updateStatusIfNeeded(BpmTaskTransferConfigDO config) {
        Integer newStatus = calculateStatus(config.getStartTime(), config.getEndTime());
        if (!newStatus.equals(config.getStatus())&& !Objects.equals(config.getStatus(), BpmTaskTransferStatusEnum.CANCELED.getStatus())) {
            BpmTaskTransferConfigDO update = new BpmTaskTransferConfigDO();
            update.setId(config.getId());
            update.setStatus(newStatus);
            transferConfigMapper.updateById(update);
            config.setStatus(newStatus);
        }
    }

    @Override
    public BpmTaskTransferConfigDO getActiveTaskTransferConfig(Long fromUserId, String processDefinitionId) {
        Long now = System.currentTimeMillis();
        java.util.List<BpmTaskTransferConfigDO> list = transferConfigMapper.selectListByUser(fromUserId, processDefinitionId, now);
        if (list.isEmpty()) {
            list = transferConfigMapper.selectListByUser(fromUserId, null, now);
        }
        return list.isEmpty() ? null : list.getFirst();
    }

    @Override
    public void putTaskTransferConfigrevoke(Long id) {
        validateExists(id);
        BpmTaskTransferConfigDO updateObj = new BpmTaskTransferConfigDO();
        updateObj.setId(id);
        updateObj.setStatus(BpmTaskTransferStatusEnum.CANCELED.getStatus());
        transferConfigMapper.updateById(updateObj);
      }

    }
