package com.itsectest.shared.security;

import java.time.Duration;

public interface TokenDenylist {

    void revoke(String tokenId, Duration ttl);

    boolean isRevoked(String tokenId);
}
