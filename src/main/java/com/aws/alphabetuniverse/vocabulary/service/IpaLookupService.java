package com.aws.alphabetuniverse.vocabulary.service;

import com.aws.alphabetuniverse.vocabulary.dto.IpaLookupResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface IpaLookupService {
   public IpaLookupResponse lookupIpa(String word, Jwt jwt);
}