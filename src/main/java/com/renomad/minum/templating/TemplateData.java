package com.renomad.minum.templating;

import java.util.*;

public class TemplateData {

    private List<Map<String, TemplateValue>> data;

    public TemplateData() {
        this.data = new ArrayList<>();
    }

    // these are all prototypes

    // TODO this is just a prototype
    public void add(String keyToInnerTemplate,
                         List<Map<String,String>> innerTemplateValues) {

        if (this.data.isEmpty()) {
            this.data.add(new HashMap<>());
        }

        // for each map in the uppermost list, we'll "put" an inner list for a particular key
        for(Map<String,TemplateValue> mapOfData : data) {
            List<Map<String, TemplateValue>> maps = convertListForTemplateValue(innerTemplateValues);
            mapOfData.put(keyToInnerTemplate, new TemplateValue(maps));
        }
    }

    public void add(Map<String, String> newMapData) {
        Map<String, TemplateValue> conversionMap = convertMapToTemplateValue(newMapData);
        data.add(conversionMap);
    }

    public void add(List<Map<String, String>> newMapDataList) {
        List<Map<String, TemplateValue>> conversionResult = convertListForTemplateValue(newMapDataList);
        data.addAll(conversionResult);
    }

    /**
     * Helper method to convert a map of (string -> string) to (string -> TemplateValue)
     */
    private static Map<String, TemplateValue> convertMapToTemplateValue(Map<String, String> newMapData) {
        Map<String, TemplateValue> conversionMap = new HashMap<>();
        for (Map.Entry<String,String> data : newMapData.entrySet()) {
            conversionMap.put(data.getKey(), new TemplateValue(data.getValue()));
        }
        return conversionMap;
    }

    /**
     * Helper method to convert a list of maps of (string -> string) to a list of maps of (string -> TemplateValue)
     */
    private static List<Map<String, TemplateValue>> convertListForTemplateValue(List<Map<String, String>> newMapDataList) {
        List<Map<String,TemplateValue>> conversionResult = new ArrayList<>();
        for (Map<String, String> newMapData : newMapDataList) {
            Map<String, TemplateValue> stringTemplateValueMap = convertMapToTemplateValue(newMapData);
            conversionResult.add(stringTemplateValueMap);
        }
        return conversionResult;
    }

    public List<Map<String, TemplateValue>> getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TemplateData that = (TemplateData) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }

    @Override
    public String toString() {
        return "TemplateData{" +
                "data=" + data +
                '}';
    }
}
