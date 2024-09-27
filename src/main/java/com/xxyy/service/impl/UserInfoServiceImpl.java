package com.xxyy.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xxyy.component.MailComponent;
import com.xxyy.config.SysSettingConfig;
import com.xxyy.dto.*;
import com.xxyy.entity.UserInfo;
import com.xxyy.entity.enums.UserStatusEnums;
import com.xxyy.mapper.FileInfoMapper;
import com.xxyy.mapper.UserInfoMapper;
import com.xxyy.service.IUserInfoService;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.CodeConstants;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author xy
 * @date 2024-09-17 21:30
 */

@Service
@Slf4j
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${admin.emails}")
    private String adminEmails;

    @Value("${project.folder}")
    private String projectFile;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JavaMailSenderImpl javaMailSender;

    @Autowired
    private MailComponent mailComponent;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> sendEmailCode(EmailLoginDTO emailLoginDTO) {
        String email = emailLoginDTO.getEmail();
        UserInfo userInfo = getBaseMapper().selectOne(new QueryWrapper<UserInfo>().eq("email", email));
        if (emailLoginDTO.getType() == CodeConstants.ZERO && userInfo != null) {
            log.warn("邮箱: " + email + "已注册");
            throw new AppException("邮箱已注册");
        }
        String code = StringTools.getRandomNumber(CodeConstants.LENGTH_5);
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_REGISTER_CODE_EMAIL + email, code
                , 5, TimeUnit.MINUTES);
        //发送邮箱验证码
        try {
            // 获取系统邮件设置
            SysSettingConfig sysSetting = mailComponent.getSysSetting();
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage());
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(sysSetting.getSendMailTitle());
            helper.setText(String.format(sysSetting.getSendMailText(), code));
            helper.setSentDate(new Date());
            javaMailSender.send(helper.getMimeMessage());
        } catch (MessagingException e) {
            log.error("发送邮件错误", e);
            throw new AppException("发送邮箱验证码错误");
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> register(EmailRegisterDTO emailRegisterDTO) {
        if (getBaseMapper().selectOne(new QueryWrapper<UserInfo>().eq("email", emailRegisterDTO.getEmail())) != null) {
            log.error("邮箱已经存在");
            throw new AppException("邮箱已经存在");
        }
        if (getOne(new QueryWrapper<UserInfo>().eq("nick_name", emailRegisterDTO.getNickName())) != null) {
            log.error("昵称被占用");
            throw new AppException("昵称被占用");
        }
        String eCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_REGISTER_CODE_EMAIL + emailRegisterDTO.getEmail());
        checkCode(emailRegisterDTO.getEmailCode(), eCode, emailRegisterDTO.getEmail());
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(StringTools.getRandomNumber(CodeConstants.LENGTH_10));
        userInfo.setNickName(emailRegisterDTO.getNickName());
        userInfo.setEmail(emailRegisterDTO.getEmail());
        userInfo.setPassword(StringTools.byMd5(emailRegisterDTO.getPassword()));
        userInfo.setUseSpace((long) CodeConstants.ZERO);
        userInfo.setTotalSpace(mailComponent.getSysSetting().getUserInitSpace() * CodeConstants.MB);
        userInfo.setRegisterTime(new Date());
        userInfo.setStatus(UserStatusEnums.EABLE.getStatus());
        save(userInfo);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginInfoVO login(HttpSession session, String email, String password) {
        UserInfo userInfo = getOne(new QueryWrapper<UserInfo>().eq("email", email));
        if (userInfo == null || !userInfo.getPassword().equals(password)) {
            throw new AppException("账号或密码错误");
        }
        if (userInfo.getStatus() == 0) {
            throw new AppException("用户已被禁用");
        }
        //  设置最后登陆时间
        String token = UUID.randomUUID().toString();
        userInfo.setLastLoginTime(new Date());
        updateById(userInfo);
        LoginInfoVO outputDTO = LoginInfoVO.of(userInfo, token);
        outputDTO.setAdmin(ArrayUtils.contains(adminEmails.split(","), email));
        // hutool工具包，将一个bean转化为一个Map，以HashMap存储，并设置value存储格式
        Map<String, Object> loginUserMap = BeanUtil.beanToMap(outputDTO, new HashMap<>(),
                CopyOptions.create().setFieldValueEditor((fieldName, fieldValue) -> fieldValue==null? null: fieldValue.toString()));
        // 将用户信息存入redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.MYPAN_LOGIN_USER_KEY + token, loginUserMap);
        stringRedisTemplate.expire(RedisConstants.MYPAN_LOGIN_USER_KEY + token, 1, TimeUnit.HOURS);
        // 获取用户空间使用情况，存入redis
        // TODO: 2024/9/20 获得用户已使用空间
        Long useSpace = fileInfoMapper.selectUseSpace(userInfo.getUserId());
        UserSpaceVO userSpaceVO = new UserSpaceVO(useSpace, userInfo.getTotalSpace());
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + userInfo.getUserId(), userSpaceVO.toJSONString(), 1, TimeUnit.HOURS);
        return outputDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        String eCode = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_REGISTER_CODE_EMAIL + email);
        checkCode(emailCode, eCode, email);
        // 修改密码
        UserInfo userInfo = getOne(new QueryWrapper<UserInfo>().eq("email", email));
        if (userInfo != null && userInfo.getStatus() != 0) {
            userInfo.setPassword(StringTools.byMd5(password));
            updateById(userInfo);
        } else {
            log.error("账号不存在或被禁用");
            throw new AppException("账号不存在或被禁用");
        }
    }

    @Override
    public void getUserAvatar(String userId, HttpServletResponse response) {
        UserInfo userInfo = getById(userId);
        if (userInfo == null) {
            throw new AppException("用户不存在");
        }
        // 创建图片文件目录
        File imageFile = new File(projectFile + CodeConstants.IMAGE_FILE);
        if (!imageFile.exists()) {
            imageFile.mkdirs();
        }
        String avatar = userInfo.getAvatar();
        if (avatar == null) {
            // 使用默认头像
            String filePath = projectFile + CodeConstants.IMAGE_FILE + CodeConstants.DEFAULT_AVATAR;
            getImageAndSend(filePath, response);
        } else {
            getImageAndSend(projectFile + CodeConstants.IMAGE_FILE + userInfo.getUserId() + CodeConstants.IMAGE_FILE_SUFFIX, response);
        }
    }

    @Override
    public Result<UserSpaceVO> getUseSpace(String token) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
        LoginInfoVO loginInfoVO = BeanUtil.fillBeanWithMap(entries, new LoginInfoVO(), true);
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.MYPAN_LOGIN_USER_SPACE + loginInfoVO.getUserId());
        if (!StringTools.isEmpty(json)) {
            return Result.data(JSON.parseObject(json, UserSpaceVO.class));
        }
        // 返回用户空间
        // TODO: 2024/9/25 返回用户空间使用情况可能有问题
        UserInfo user = getOne(new QueryWrapper<UserInfo>().eq("user_id", loginInfoVO.getUserId()));
        UserSpaceVO userSpaceVO = new UserSpaceVO();
        userSpaceVO.setUseSpace(user.getUseSpace());
        userSpaceVO.setTotalSpace(user.getTotalSpace());
        stringRedisTemplate.opsForValue().set(RedisConstants.MYPAN_LOGIN_USER_SPACE + loginInfoVO.getUserId(),
                JSON.toJSONString(userSpaceVO), 1, TimeUnit.HOURS);
        return Result.data(userSpaceVO);

    }

    @Override
    public void logout(String token) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
        String userId = (String) entries.get("userId");
        //  清除用户信息
        stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_USER_SPACE + userId);
        stringRedisTemplate.delete(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(MultipartFile avatarFile, String token) throws IOException {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token);
        Object userId = entries.get("userId");
        UserInfo userInfo = getOne(new QueryWrapper<UserInfo>().eq("user_id", userId));
        // 获得文件内容
        byte[] bytes = avatarFile.getBytes();
        String filePath = projectFile + CodeConstants.IMAGE_FILE + userInfo.getUserId() + CodeConstants.IMAGE_FILE_SUFFIX;
        // 指定文件路径
        Path path = Paths.get(filePath);
        // 写入文件到指定路径
        Files.write(path, bytes);
        // 保存数据到mysql
        userInfo.setAvatar("/api/getAvatar/" + userInfo.getUserId());
        entries.put("avatar", "/api/getAvatar/" + userInfo.getUserId());
        userInfo.setLastLoginTime(new Date());
        // 跟新后的头像存入redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.MYPAN_LOGIN_USER_KEY + token, entries);
        stringRedisTemplate.expire(RedisConstants.MYPAN_LOGIN_USER_KEY + token, 1, TimeUnit.HOURS);
        updateById(userInfo);
    }

    private void checkCode(String code, String eCode, String email) {
        if (eCode == null) {
            log.error("验证码已失效");
            throw new AppException("验证码已失效");
        }
        try {
            if (!code.equals(eCode)) {
                log.error("验证码错误");
                throw new AppException("验证码错误");
            }
        } finally {
            stringRedisTemplate.delete(RedisConstants.MYPAN_REGISTER_CODE_EMAIL + email);
        }
    }

    private void getImageAndSend(String filePath, HttpServletResponse response) {
        if (filePath.contains("../") || filePath.contains("..\\")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw new AppException("文件不存在");
        }
        // 设置响应类型
        response.setContentType("image/jpeg");
        try(FileInputStream fileInputStream = new FileInputStream(file);
            ServletOutputStream outputStream = response.getOutputStream()) {
            // 将数据读入字节缓冲区
            byte[] buffer = new byte[1024];
            int byteRead = 1;
            while ((byteRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, byteRead);
            }
            // 确保刷新并关闭
            outputStream.flush();
        } catch (IOException e) {
            throw new AppException("读取文件出错");
        }
    }
}
