package com.dcj.meeting.service.impl;

import com.dcj.meeting.mapper.AppointmentMapper;
import com.dcj.meeting.mapper.CheckViewMapper;
import com.dcj.meeting.pojo.enterprise.AppProperties;
import com.dcj.meeting.pojo.enterprise.message.UserTextMessage;
import com.dcj.meeting.pojo.entity.Appointment;
import com.dcj.meeting.pojo.entity.AppointmentStatus;
import com.dcj.meeting.pojo.entity.Period;
import com.dcj.meeting.pojo.entity.view.CheckView;
import com.dcj.meeting.service.AppointmentService;
import com.dcj.meeting.service.CheckService;
import com.dcj.meeting.service.MessageService;
import com.dcj.meeting.service.RoomService;
import com.dcj.meeting.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class CheckServiceImpl implements CheckService {
    @Autowired
    AppProperties appProperties;
    @Autowired
    AppointmentMapper appointmentMapper;
    @Autowired
    RoomService roomService;
    @Autowired
    MessageService messageService;
    @Autowired
    CheckViewMapper checkViewMapper;
    @Autowired
    AppointmentService appointmentService;

    @Override
    public List<CheckView> selectChecking() {
        return checkViewMapper.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approveAll(String checker) {
        List<Appointment> appointments = appointmentService.selectByStatusCode(AppointmentStatus.CHECKING);
        for (Appointment appointment : appointments) {
            approve(appointment, checker);
        }
        return appointments.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(String checker, List<Appointment> appointments, String checkInfo) {
        for (Appointment appointment : appointments) {
            reject(appointment, checker, checkInfo);
        }
        return appointments.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Appointment appointment, String checker, String checkInfo) {
        appointment.setStatusCode(AppointmentStatus.REJECTED);
        appointment.setCheckInfo(checker + "???" + checkInfo);
        int i = appointmentService.updateAppointment(appointment);
        //????????????????????????????????????????????????
        UserTextMessage message = new UserTextMessage();
        message.setAgentid(Integer.valueOf(appProperties.getAgentid()));
        message.setTouser(appointment.getUserid());
        message.setContent(formatted(appointment, "?????????\n???????????????" + checkInfo));
        messageService.sendUserTextMessage(message);
        return i;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(Appointment appointment, String checker) {
        int index = 0;
        int roomId = appointment.getRoomId();
        Period period = DateUtil.buildPeriod(appointment.getStartTime(), appointment.getDuration());
        //???????????????????????????????????????
        if (!roomService.isAvailableDuring(roomId, period)) {
            //????????????????????????,???????????????????????????
            appointment.setStatusCode(AppointmentStatus.REJECTED);
            appointment.setCheckInfo(checker + "???????????????????????????");
            appointmentMapper.updateAppointment(appointment);
            //?????????????????????????????????????????????
            UserTextMessage message = new UserTextMessage();
            message.setAgentid(Integer.valueOf(appProperties.getAgentid()));
            message.setTouser(appointment.getUserid());
            message.setContent(formatted(appointment, "????????????????????????"));
            messageService.sendUserTextMessage(message);
        } else {
            //????????????????????????????????????
            appointment.setStatusCode(AppointmentStatus.PASSED);
            appointment.setCheckInfo(checker + "?????????????????????");
            index = appointmentMapper.updateAppointment(appointment);
            if (index > 0) {
                //???????????????????????????????????????
                UserTextMessage message = new UserTextMessage();
                message.setAgentid(Integer.valueOf(appProperties.getAgentid()));
                message.setTouser(appointment.getUserid());
                message.setContent(formatted(appointment, "??????????????????"));
                messageService.sendUserTextMessage(message);
                //??????????????????????????????????????????
                List<Appointment> checkings = appointmentMapper.selectByStatusCode(AppointmentStatus.CHECKING);
                for (Appointment checking : checkings) {
                    Period p = DateUtil.buildPeriod(checking.getStartTime(), checking.getDuration());
                    if (!roomService.isAvailableDuring(checking.getRoomId(), p)) {
                        reject(checking, checker, "????????????????????????");
                    }
                }
            }
        }
        return index;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(List<Appointment> appointments, String checker) {
        int count = 0;
        for (Appointment appointment : appointments) {
            if (approve(appointment, checker) > 0) {
                count++;
            }
        }
        return count;
    }

    //????????????????????????
    public static String formatted(Appointment appointment, String content) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        String date = format.format(new Date(System.currentTimeMillis()));
        return "???????????????????????????\n???????????????" + appointment.getAppointId() + "\n???????????????" + content + "\n???????????????" + date;
    }
}
