package dev.aibuilder;

import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative textual feature extraction. Returns zero when the requirement
 * is not clearly stated; never guesses number of towers from the building
 * category and never creates or chooses prefab geometry.
 */
public final class RequestedFeatures {
    private RequestedFeatures() {}

    private static final Map<String,Integer> COUNTS=Map.ofEntries(
        Map.entry("один",1),Map.entry("одну",1),
        Map.entry("два",2),Map.entry("две",2),
        Map.entry("три",3),Map.entry("тремя",3),
        Map.entry("четыре",4),Map.entry("четырьмя",4),
        Map.entry("пять",5),Map.entry("пятью",5),
        Map.entry("шесть",6),Map.entry("шестью",6),
        Map.entry("семь",7),Map.entry("восемь",8),
        Map.entry("девять",9),Map.entry("десять",10),
        Map.entry("one",1),Map.entry("two",2),
        Map.entry("three",3),Map.entry("four",4),
        Map.entry("five",5),Map.entry("six",6),
        Map.entry("seven",7),Map.entry("eight",8),
        Map.entry("nine",9),Map.entry("ten",10));
    private static final Pattern TOWERS=Pattern.compile(
        "(?:\\b(\\d{1,2}|один|одну|два|две|три|тремя|четыре|четырьмя|пять|пятью|"
        +"шесть|шестью|семь|восемь|девять|десять|one|two|three|four|five|"
        +"six|seven|eight|nine|ten)\\b)"
        +"\\s+(?:(?:мощные|мощных|угловые|угловых|высокие|высоких|разные|"
        +"разных|разной|высоты|большие|больших|каменные|каменных|"
        +"круглые|круглых|декоративные|small|large|tall|corner|"
        +"different|stone|round)\\s+){0,4}"
        +"(башн[а-яё]*|башен|towers?)\\b",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern FLOORS=Pattern.compile(
        "(?:\\b(\\d{1,2}|один|два|две|три|четыре|пять|one|two|three|four|five)\\b)"
        +"\\s+(этаж[аеёйов]*|floors?|stories)",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|
            Pattern.UNICODE_CHARACTER_CLASS);

    public static int towers(String prompt){return getCount(TOWERS,prompt);}
    public static int floors(String prompt){return getCount(FLOORS,prompt);}

    private static int getCount(Pattern regex,String prompt){
        if(prompt==null)return 0;
        Matcher matcher=regex.matcher(prompt.toLowerCase(Locale.ROOT));
        if(!matcher.find())return 0;
        String count=matcher.group(1);
        if(count.matches("\\d{1,2}"))
            return Math.min(99,Integer.parseInt(count));
        return COUNTS.getOrDefault(count,0);
    }
}
