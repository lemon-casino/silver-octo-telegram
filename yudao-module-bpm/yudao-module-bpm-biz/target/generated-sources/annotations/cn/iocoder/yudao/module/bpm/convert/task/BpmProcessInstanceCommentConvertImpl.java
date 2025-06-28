package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-06-28T10:48:28+0800",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.6 (JetBrains s.r.o.)"
)
public class BpmProcessInstanceCommentConvertImpl implements BpmProcessInstanceCommentConvert {

    @Override
    public BpmProcessInstanceCommentRespVO convert(BpmProcessInstanceCommentDO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessInstanceCommentRespVO bpmProcessInstanceCommentRespVO = new BpmProcessInstanceCommentRespVO();

        bpmProcessInstanceCommentRespVO.setId( bean.getId() );
        bpmProcessInstanceCommentRespVO.setProcessInstanceId( bean.getProcessInstanceId() );
        bpmProcessInstanceCommentRespVO.setUserId( bean.getUserId() );
        bpmProcessInstanceCommentRespVO.setUserNickname( bean.getUserNickname() );
        bpmProcessInstanceCommentRespVO.setUserAvatar( bean.getUserAvatar() );
        bpmProcessInstanceCommentRespVO.setContent( bean.getContent() );
        List<String> list = bean.getPicUrls();
        if ( list != null ) {
            bpmProcessInstanceCommentRespVO.setPicUrls( new ArrayList<String>( list ) );
        }
        bpmProcessInstanceCommentRespVO.setCreateTime( bean.getCreateTime() );
        List<Long> list1 = bean.getAtUserIds();
        if ( list1 != null ) {
            bpmProcessInstanceCommentRespVO.setAtUserIds( new ArrayList<Long>( list1 ) );
        }

        return bpmProcessInstanceCommentRespVO;
    }

    @Override
    public List<BpmProcessInstanceCommentRespVO> convertList(List<BpmProcessInstanceCommentDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmProcessInstanceCommentRespVO> list1 = new ArrayList<BpmProcessInstanceCommentRespVO>( list.size() );
        for ( BpmProcessInstanceCommentDO bpmProcessInstanceCommentDO : list ) {
            list1.add( convert( bpmProcessInstanceCommentDO ) );
        }

        return list1;
    }
}
