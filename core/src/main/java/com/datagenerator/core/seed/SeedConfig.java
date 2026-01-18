package com.datagenerator.core.seed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Value;

/** Base class for seed configuration. Supports embedded, file, env, and remote seed sources. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SeedConfig.EmbeddedSeed.class, name = "embedded"),
  @JsonSubTypes.Type(value = SeedConfig.FileSeed.class, name = "file"),
  @JsonSubTypes.Type(value = SeedConfig.EnvSeed.class, name = "env"),
  @JsonSubTypes.Type(value = SeedConfig.RemoteSeed.class, name = "remote")
})
public abstract sealed class SeedConfig
    permits SeedConfig.EmbeddedSeed,
        SeedConfig.FileSeed,
        SeedConfig.EnvSeed,
        SeedConfig.RemoteSeed {
  @NotNull private final String type;

  protected SeedConfig(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  @Value
  @EqualsAndHashCode(callSuper = false)
  public static class EmbeddedSeed extends SeedConfig {
    long value;

    @JsonCreator
    public EmbeddedSeed(@JsonProperty("type") String type, @JsonProperty("value") long value) {
      super(type);
      this.value = value;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = false)
  public static class FileSeed extends SeedConfig {
    @NotNull String path;

    @JsonCreator
    public FileSeed(@JsonProperty("type") String type, @JsonProperty("path") String path) {
      super(type);
      this.path = path;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = false)
  public static class EnvSeed extends SeedConfig {
    @NotNull String name;

    @JsonCreator
    public EnvSeed(@JsonProperty("type") String type, @JsonProperty("name") String name) {
      super(type);
      this.name = name;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = false)
  public static class RemoteSeed extends SeedConfig {
    @NotNull String url;

    AuthConfig auth;

    @JsonCreator
    public RemoteSeed(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("auth") AuthConfig auth) {
      super(type);
      this.url = url;
      this.auth = auth;
    }

    @Value
    public static class AuthConfig {
      @NotNull String type; // bearer, basic, api_key

      String token; // for bearer
      String username; // for basic
      String password; // for basic
      String key; // for api_key - header name
      String value; // for api_key - header value

      @JsonCreator
      public AuthConfig(
          @JsonProperty("type") String type,
          @JsonProperty("token") String token,
          @JsonProperty("username") String username,
          @JsonProperty("password") String password,
          @JsonProperty("key") String key,
          @JsonProperty("value") String value) {
        this.type = type;
        this.token = token;
        this.username = username;
        this.password = password;
        this.key = key;
        this.value = value;
      }
    }
  }
}
