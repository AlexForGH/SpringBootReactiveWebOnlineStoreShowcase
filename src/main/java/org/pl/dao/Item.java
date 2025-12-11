package org.pl.dao;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table(name = "items")
public class Item {
    @Id
    @Column("id")
    private Long id;
    @Column("title")
    private String title;
    @Column("img_path")
    private String imgPath;
    @Column("price")
    private BigDecimal price;
    @Column("description")
    private String description;

    public Item() {
    }

    public Item(String title, String imgPath, BigDecimal price, String description) {
        this.title = title;
        this.imgPath = imgPath;
        this.price = price;
        this.description = description;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImgPath() {
        return imgPath;
    }

    public void setImgPath(String imgPath) {
        this.imgPath = imgPath;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
