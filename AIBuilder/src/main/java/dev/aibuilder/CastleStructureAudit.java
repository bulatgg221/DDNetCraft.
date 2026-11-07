package dev.aibuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic quality gate for original AI-authored castles.
 *
 * It checks actual voxel locations, not merely plausible JSON declarations.
 * It NEVER substitutes a template/house/castle. Critical omissions block
 * confirmation while the generated project can still be inspected or saved.
 *
 * This is not a computer-vision aesthetic scorer: sophisticated, properly
 * connected layouts can still look unattractive in-game.
 */
public final class CastleStructureAudit {
    private CastleStructureAudit() {}

    public record Findings(List<String> blockers, List<String> warnings) {
        public boolean canBuild() { return blockers.isEmpty(); }
    }

    public static boolean isCastle(String prompt) {
        if(prompt==null)return false;
        String p=prompt.toLowerCase(Locale.ROOT);
        return p.contains("замок") || p.contains("замка")
            || p.contains("крепост") || p.contains("цитадел")
            || p.contains("castle") || p.contains("fortress")
            || p.contains("citadel");
    }

    public static Findings inspect(String prompt,List<ArchitectureSpec.Region> regions,
                                   Map<WalkabilityAudit.Cell,String> blocks) {
        if(!isCastle(prompt))return new Findings(List.of(),List.of());
        List<String> blockers=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        if(regions.isEmpty()) {
            blockers.add("Замок без architecture.regions: нельзя проверить "
                +"башни, главный вход и связность. Попроси модель составить полноценный план.");
            return new Findings(List.copyOf(blockers),List.copyOf(warnings));
        }
        List<ArchitectureSpec.Region> towers=regions.stream()
            .filter(CastleStructureAudit::tower).toList();
        int expected=RequestedFeatures.towers(prompt);
        if(expected>0 && towers.size()<expected)
            blockers.add("Запрошено "+expected+" башен, но модель описала только "
                +towers.size()+" в architecture.regions.");
        else if(expected==0 && towers.size()<2)
            warnings.add("Для крепости размечено менее двух башен.");

        int actual=0;
        for(ArchitectureSpec.Region tower:towers) {
            if(hasActualTower(tower,blocks))actual++;
            else blockers.add("Башня "+tower.id()+" объявлена в JSON, но её "
                +"форма не построена полностью (нужна объёмная высокая конструкция).");
        }
        if(expected>0 && actual<expected)
            warnings.add("В чертеже фактически обнаружено только "+actual+
                " завершённых башен из "+expected+" запрошенных.");

        boolean wantsGate=requiresGate(prompt);
        if(wantsGate) {
            List<ArchitectureSpec.Region> gates=regions.stream()
                .filter(CastleStructureAudit::gate).toList();
            if(gates.isEmpty())
                blockers.add("Для замка не описана главная воротная зона "
                    +"(kind=gatehouse или entrance).");
            else {
                boolean hasWideOpening=gates.stream().anyMatch(
                    r->hasFramedOpening(r,blocks));
                if(!hasWideOpening)
                    blockers.add("Главные ворота отсутствуют или слишком узкие: "
                        +"нужен реальный проход минимум 3×3 блока между колоннами, "
                        +"а не одиночная дверь сбоку.");
            }
        }
        if((prompt.toLowerCase(Locale.ROOT).contains("дворец")
            ||prompt.toLowerCase(Locale.ROOT).contains("palace")
            ||prompt.toLowerCase(Locale.ROOT).contains("central keep"))
            && regions.stream().noneMatch(CastleStructureAudit::keep))
            warnings.add("Запрошен центральный дворец/донжон, "
                +"но в architecture.regions нет соответствующей зоны.");

        return new Findings(List.copyOf(blockers),List.copyOf(warnings));
    }

    private static boolean tower(ArchitectureSpec.Region r) {
        return r.kind().contains("tower") || r.kind().contains("башн")
            || r.id().contains("tower") || r.id().contains("башн");
    }
    private static boolean gate(ArchitectureSpec.Region r) {
        return r.kind().contains("gate") || r.kind().contains("entrance")
            || r.kind().contains("entry") || r.kind().contains("вход")
            || r.kind().contains("ворот") || r.id().contains("gate")
            || r.id().contains("entrance") || r.id().contains("ворот");
    }
    private static boolean keep(ArchitectureSpec.Region r) {
        return r.kind().contains("keep") || r.kind().contains("palace")
            || r.kind().contains("дворец") || r.kind().contains("донжон")
            || r.id().contains("keep") || r.id().contains("palace");
    }
    private static boolean requiresGate(String s) {
        String p=s.toLowerCase(Locale.ROOT);
        return p.contains("ворот")||p.contains("главный вход")
            ||p.contains("gatehouse")||p.contains("main gate")
            ||p.contains("entry gate")||p.contains("entrance gate");
    }
    private static boolean isOpen(String material) {
        return material==null || material.equals("air")
            || (material.endsWith("_door")&&!material.endsWith("_trapdoor"));
    }
    private static boolean solid(String material) {
        return material!=null && !isOpen(material)
            && !material.equals("water") && !material.equals("lava")
            && !material.endsWith("_torch") && !material.endsWith("_pane")
            && !material.endsWith("_carpet") && !material.equals("chain");
    }

    /** Real height AND a broad enough footprint, not only metadata. */
    private static boolean hasActualTower(ArchitectureSpec.Region r,
                                         Map<WalkabilityAudit.Cell,String> blocks) {
        if(r.height()<6 || r.width()<3 || r.depth()<3)return false;
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        Set<Integer> uniqueX=new HashSet<>(),uniqueZ=new HashSet<>();
        int occupied=0;
        for(var e:blocks.entrySet()){
            WalkabilityAudit.Cell c=e.getKey();
            if(!r.contains(c.x(),c.y(),c.z())||!solid(e.getValue()))continue;
            occupied++;
            low=Math.min(low,c.y());high=Math.max(high,c.y());
            uniqueX.add(c.x());uniqueZ.add(c.z());
        }
        int neededHeight=Math.max(5,(r.height()+1)/2);
        return high>=low && high-low+1>=neededHeight
            && uniqueX.size()>=3 && uniqueZ.size()>=3
            && occupied>=Math.max(18,r.height()*3);
    }

    /**
     * Find an actual clear 3 x 3 passage framed by solid columns near the
     * declared entrance. Works in either X-facing or Z-facing orientation.
     * A big blank courtyard cannot pass: frame supports must really exist.
     */
    private static boolean hasFramedOpening(ArchitectureSpec.Region r,
                                           Map<WalkabilityAudit.Cell,String> voxels) {
        if(r.width()<3 && r.depth()<3)return false;
        int endY=Math.min(r.y()+r.height()-3,r.y()+4);
        for(int y=r.y();y<=endY;y++){
            for(int x=r.x();x<r.x()+r.width();x++)
                for(int z=r.z();z<r.z()+r.depth();z++){
                    // Gate opening along X with side columns on the two ends
                    if(clearX(voxels,x,y,z) &&
                        framedX(voxels,x,y,z))return true;
                    // Gate opening along Z with end columns
                    if(clearZ(voxels,x,y,z) &&
                        framedZ(voxels,x,y,z))return true;
                }
        }
        return false;
    }
    private static String at(Map<WalkabilityAudit.Cell,String> m,int x,int y,int z){
        return m.get(new WalkabilityAudit.Cell(x,y,z));
    }
    private static boolean clearX(Map<WalkabilityAudit.Cell,String> b,int x,int y,int z){
        for(int dx=0;dx<3;dx++)for(int dy=0;dy<3;dy++)
            if(!isOpen(at(b,x+dx,y+dy,z)))return false;
        return true;
    }
    private static boolean clearZ(Map<WalkabilityAudit.Cell,String> b,int x,int y,int z){
        for(int dz=0;dz<3;dz++)for(int dy=0;dy<3;dy++)
            if(!isOpen(at(b,x,y+dy,z+dz)))return false;
        return true;
    }
    private static boolean framedX(Map<WalkabilityAudit.Cell,String> b,int x,int y,int z){
        for(int dy=0;dy<3;dy++)
            if(!solid(at(b,x-1,y+dy,z)) ||
               !solid(at(b,x+3,y+dy,z)))return false;
        return true;
    }
    private static boolean framedZ(Map<WalkabilityAudit.Cell,String> b,int x,int y,int z){
        for(int dy=0;dy<3;dy++)
            if(!solid(at(b,x,y+dy,z-1)) ||
               !solid(at(b,x,y+dy,z+3)))return false;
        return true;
    }
}
