package com.renomad.minum.templating;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TemplateValue {

    private List<Map<String, TemplateValue>> innerData;
    private String value;
    private TemplateValueType templateValueType;

    public TemplateValue(List<Map<String, TemplateValue>> innerData) {
        this.innerData = innerData;
        this.templateValueType = TemplateValueType.LIST_OF_MAPS;
    }

    public TemplateValue(String value) {
        this.value = value;
        this.templateValueType = TemplateValueType.STRING;
    }

    public TemplateValueType getTemplateValueType() {
        return templateValueType;
    }

    public List<Map<String, TemplateValue>> getInnerData() {
        return innerData;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TemplateValue that = (TemplateValue) o;
        return Objects.equals(innerData, that.innerData) && Objects.equals(value, that.value) && templateValueType == that.templateValueType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(innerData, value, templateValueType);
    }

    @Override
    public String toString() {
        if (templateValueType.equals(TemplateValueType.STRING)) {
            return this.value;
        } else {
            return this.innerData.toString();
        }
    }
}