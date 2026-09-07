package com.mrecoder.errortime.example.demo.record;

/** Fake directory entry returned by {@link com.mrecoder.errortime.example.demo.service.LdapService}. */
public record LdapUser(String username, String distinguishedName) {
}
