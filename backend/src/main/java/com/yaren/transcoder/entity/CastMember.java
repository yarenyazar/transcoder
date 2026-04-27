package com.yaren.transcoder.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "cast_members")
public class CastMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Director, Actor, Writer etc.
    // In a complex app, role could be part of the JoinTable metadata, but we keep
    // it simple here.
    private String role;

    private String imageUrl;

    @ManyToMany(mappedBy = "castMembers")
    @JsonIgnore
    private Set<VodContent> contents = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Set<VodContent> getContents() {
        return contents;
    }

    public void setContents(Set<VodContent> contents) {
        this.contents = contents;
    }
}
