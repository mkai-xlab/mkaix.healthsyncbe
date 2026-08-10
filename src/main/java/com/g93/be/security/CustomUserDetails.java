package com.g93.be.security;

import com.g93.be.dto.PermissionResponse;

import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final List<PermissionResponse> permissions;

    public CustomUserDetails(User user, List<PermissionResponse> permissions) {
        this.user = user;
        this.permissions = permissions;
    }

    public List<String> getPermissionCodes() {
        if (permissions == null)
            return java.util.Collections.emptyList();
        return permissions.stream()
                .filter(java.util.Objects::nonNull)
                .map(p -> p.code())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
        getPermissionCodes().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }

    public User getUser() {
        return user;
    }

    public List<PermissionResponse> getPermissions() {
        return permissions;
    }
}
