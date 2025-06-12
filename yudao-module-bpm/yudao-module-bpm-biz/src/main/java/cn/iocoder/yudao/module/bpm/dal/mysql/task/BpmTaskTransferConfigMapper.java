package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface BpmTaskTransferConfigMapper extends BaseMapperX<BpmTaskTransferConfigDO> {

    default PageResult<BpmTaskTransferConfigDO> selectPage(BpmTaskTransferConfigPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmTaskTransferConfigDO>()
                .eqIfPresent(BpmTaskTransferConfigDO::getFromUserId, reqVO.getFromUserId())
                .eqIfPresent(BpmTaskTransferConfigDO::getToUserId, reqVO.getToUserId())
                .eqIfPresent(BpmTaskTransferConfigDO::getModelId, reqVO.getModelId())
                .eqIfPresent(BpmTaskTransferConfigDO::getModelVersion, reqVO.getModelVersion())
                .betweenIfPresent(BpmTaskTransferConfigDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(BpmTaskTransferConfigDO::getStatus, reqVO.getStatus())
                .orderByDesc(BpmTaskTransferConfigDO::getId));
    }
    default BpmTaskTransferConfigDO selectFirstByUser(Long fromUserId, String modelId, Integer modelVersion, Long now) {
        LambdaQueryWrapperX<BpmTaskTransferConfigDO> queryWrapper = (LambdaQueryWrapperX<BpmTaskTransferConfigDO>) new LambdaQueryWrapperX<BpmTaskTransferConfigDO>()
                .eq(BpmTaskTransferConfigDO::getFromUserId, fromUserId)
                .le(BpmTaskTransferConfigDO::getStartTime, now)
                .ge(BpmTaskTransferConfigDO::getEndTime, now)
                .ne(BpmTaskTransferConfigDO::getStatus, cn.iocoder.yudao.module.bpm.enums.transfer.BpmTaskTransferStatusEnum.CANCELED.getStatus())
                .orderByDesc(BpmTaskTransferConfigDO::getId)
                .last("LIMIT 1"); // 只选择一条记录

        // 如果modelId为null，查询model_id为空字符串或null的记录
        if (modelId == null) {
            queryWrapper.and(wrapper -> wrapper.eq(BpmTaskTransferConfigDO::getModelId, "").or().isNull(BpmTaskTransferConfigDO::getModelId));
        } else {
            queryWrapper.eq(BpmTaskTransferConfigDO::getModelId, modelId);
        }
        if (modelId == null) {
            queryWrapper.and(wrapper -> wrapper.eq(BpmTaskTransferConfigDO::getModelVersion, "").or().isNull(BpmTaskTransferConfigDO::getModelVersion));
        } else {
            queryWrapper.eq(BpmTaskTransferConfigDO::getModelVersion, modelVersion);
        }
        // 这里假设selectOne方法可以获取到单个对象
        return selectOne(queryWrapper);
    }
    default void updateByIdWithNull(BpmTaskTransferConfigDO updateObj) {
        LambdaUpdateWrapper<BpmTaskTransferConfigDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BpmTaskTransferConfigDO::getId, updateObj.getId());
        updateWrapper.set(BpmTaskTransferConfigDO::getFromUserId, updateObj.getFromUserId());
        updateWrapper.set(BpmTaskTransferConfigDO::getToUserId, updateObj.getToUserId());
        updateWrapper.set(BpmTaskTransferConfigDO::getModelId, updateObj.getModelId());
        updateWrapper.set(BpmTaskTransferConfigDO::getModelVersion, updateObj.getModelVersion());
        updateWrapper.set(BpmTaskTransferConfigDO::getStartTime, updateObj.getStartTime());
        updateWrapper.set(BpmTaskTransferConfigDO::getEndTime, updateObj.getEndTime());
        updateWrapper.set(BpmTaskTransferConfigDO::getStatus, updateObj.getStatus());
        updateWrapper.set(BpmTaskTransferConfigDO::getReason, updateObj.getReason());
        updateWrapper.set(BpmTaskTransferConfigDO::getUpdateTime, LocalDateTime.now());
        updateWrapper.set(BpmTaskTransferConfigDO::getUpdater, updateObj.getUpdater());
        // 执行更新操作
        update(updateWrapper);
    }



}
