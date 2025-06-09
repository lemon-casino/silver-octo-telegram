package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer.BpmTaskTransferConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskTransferConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BpmTaskTransferConfigMapper extends BaseMapperX<BpmTaskTransferConfigDO> {

    default PageResult<BpmTaskTransferConfigDO> selectPage(BpmTaskTransferConfigPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmTaskTransferConfigDO>()
                .eqIfPresent(BpmTaskTransferConfigDO::getFromUserId, reqVO.getFromUserId())
                .eqIfPresent(BpmTaskTransferConfigDO::getToUserId, reqVO.getToUserId())
                .eqIfPresent(BpmTaskTransferConfigDO::getProcessDefinitionId, reqVO.getProcessDefinitionId())
                .betweenIfPresent(BpmTaskTransferConfigDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmTaskTransferConfigDO::getId));
    }
    default java.util.List<BpmTaskTransferConfigDO> selectListByUser(Long fromUserId, String processDefinitionId, Long now) {
        return selectList(new LambdaQueryWrapperX<BpmTaskTransferConfigDO>()
                .eq(BpmTaskTransferConfigDO::getFromUserId, fromUserId)
                .eqIfPresent(BpmTaskTransferConfigDO::getProcessDefinitionId, processDefinitionId)
                .le(BpmTaskTransferConfigDO::getStartTime, now)
                .ge(BpmTaskTransferConfigDO::getEndTime, now)
                .ne(BpmTaskTransferConfigDO::getStatus, cn.iocoder.yudao.module.bpm.enums.transfer.BpmTaskTransferStatusEnum.CANCELED.getStatus())
                .orderByDesc(BpmTaskTransferConfigDO::getId));
    }
}
