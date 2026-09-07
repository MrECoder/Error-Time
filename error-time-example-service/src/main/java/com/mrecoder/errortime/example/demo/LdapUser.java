package com.mrecoder.errortime.example.demo;

/** Fake directory entry returned by {@link LdapService}. */
public record LdapUser(String username, String distinguishedName) {
}
