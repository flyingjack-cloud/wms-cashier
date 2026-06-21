package top.flyingjack.cashier.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import top.flyingjack.cashier.entity.SystemAuthority;
import top.flyingjack.cashier.entity.WmsUserProfile;
import top.flyingjack.cashier.mapper.AuthorityMapper;
import top.flyingjack.cashier.mapper.WmsUserProfileMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final WmsUserProfileMapper profileMapper;
    private final AuthorityMapper authorityMapper;

    public CustomJwtAuthenticationConverter(WmsUserProfileMapper profileMapper, AuthorityMapper authorityMapper) {
        this.profileMapper = profileMapper;
        this.authorityMapper = authorityMapper;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());

        WmsUserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new WmsUserProfile(userId, 0, SystemAuthority.Role.DEFAULT.value());
            profileMapper.insert(profile);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(profile.getRole()));
        authorityMapper.findAuthoritiesByUserId(userId)
                .forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
