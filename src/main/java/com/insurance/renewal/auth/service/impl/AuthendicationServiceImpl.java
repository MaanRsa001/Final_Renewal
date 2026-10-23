package com.insurance.renewal.auth.service.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.insurance.renewal.auth.bean.LoginMaster;
import com.insurance.renewal.auth.bean.LoginMasterId;
import com.insurance.renewal.auth.token.EncryDecryService;
import com.insurance.renewal.repository.LoginMasterRepository;

@Lazy
@Service
public class AuthendicationServiceImpl implements UserDetailsService {

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private LoginMasterRepository loginRepo;

    @Autowired
    private EncryDecryService endecryService;

    private Logger log = LogManager.getLogger(AuthendicationServiceImpl.class);

    @SuppressWarnings("static-access")
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoginMaster userList = new LoginMaster();
        try {
            log.info("loadUserByUsername==>" + username);

            String[] split = username.split(":");

            LoginMasterId id = new LoginMasterId();
            id.setLoginId(split[0]);

            LoginMaster userListopt = loginRepo.findByLoginId(split[0]);
            if (userListopt != null) {
                userList = userListopt;
            }
            if (userList != null) {
                String pass = bCryptPasswordEncoder.encode(endecryService.decrypt("zQYgCo7GMZeX1tBQyzAi8Q=="));
                userList.setPassword(pass);
                Set<GrantedAuthority> grantedAuthorities = new HashSet<GrantedAuthority>();
                grantedAuthorities.add(new SimpleGrantedAuthority("ADMIN"));
                log.info("loadUserByTokenEncrypt==>" + userList.getPassword());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new org.springframework.security.core.userdetails.User(
                userList.getLoginId(), userList.getPassword(), getAuthority());
    }

    private List<SimpleGrantedAuthority> getAuthority() {
        return Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

}