package net.blazingpractice.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/** Lightweight party lifecycle with leader ownership and expiring invitations. */
public final class PartyService {
    private final Map<UUID, Party> partiesByMember = new HashMap<>();
    private final Map<UUID, Invitation> invitationsByTarget = new HashMap<>();
    private final int maximumSize;
    private final long inviteExpiryMillis;

    public PartyService(int maximumSize, int inviteExpirySeconds) {
        this.maximumSize = Math.max(2, maximumSize);
        this.inviteExpiryMillis = Math.max(5, inviteExpirySeconds) * 1000L;
    }

    public boolean create(Player leader) {
        if (partiesByMember.containsKey(leader.getUniqueId())) return false;
        Party party = new Party(leader.getUniqueId());
        partiesByMember.put(leader.getUniqueId(), party);
        leader.sendMessage("§aParty created. Invite players with §f/party invite <player>§a.");
        return true;
    }

    public boolean invite(Player leader, Player target) {
        Party party = partiesByMember.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId()) || target.equals(leader)
                || partiesByMember.containsKey(target.getUniqueId()) || party.members.size() >= maximumSize) return false;
        invitationsByTarget.put(target.getUniqueId(), new Invitation(leader.getUniqueId(), System.currentTimeMillis() + inviteExpiryMillis));
        target.sendMessage("§e" + leader.getName() + " invited you to a party. Use §f/party accept§e within 60 seconds.");
        leader.sendMessage("§aParty invitation sent to " + target.getName() + ".");
        return true;
    }

    public boolean accept(Player target) {
        Invitation invitation = invitationsByTarget.remove(target.getUniqueId());
        if (invitation == null || invitation.expiresAt < System.currentTimeMillis()) return false;
        Party party = partiesByMember.get(invitation.leader);
        Player leader = Bukkit.getPlayer(invitation.leader);
        if (party == null || leader == null || !leader.isOnline() || party.members.size() >= maximumSize
                || partiesByMember.containsKey(target.getUniqueId())) return false;
        party.members.add(target.getUniqueId()); partiesByMember.put(target.getUniqueId(), party);
        broadcast(party, "§a" + target.getName() + " joined the party.");
        return true;
    }

    public boolean leave(Player player) {
        Party party = partiesByMember.remove(player.getUniqueId());
        if (party == null) return false;
        party.members.remove(player.getUniqueId());
        if (party.members.isEmpty()) {
            player.sendMessage("§eParty disbanded.");
            return true;
        }
        if (party.leader.equals(player.getUniqueId())) {
            party.leader = party.members.iterator().next();
            Player next = Bukkit.getPlayer(party.leader);
            broadcast(party, "§eParty leader changed" + (next == null ? "." : " to " + next.getName() + "."));
        } else broadcast(party, "§e" + player.getName() + " left the party.");
        return true;
    }

    public boolean chat(Player player, String message) {
        Party party = partiesByMember.get(player.getUniqueId());
        if (party == null) return false;
        broadcast(party, "§dParty §7" + player.getName() + "§f: " + message);
        return true;
    }

    public String info(Player player) {
        Party party = partiesByMember.get(player.getUniqueId());
        if (party == null) return "§cYou are not in a party.";
        Player leader = Bukkit.getPlayer(party.leader);
        List<String> names = new ArrayList<>();
        for (UUID id : party.members) { Player member = Bukkit.getPlayer(id); names.add(member == null ? id.toString().substring(0, 8) : member.getName()); }
        return "§aParty leader: §f" + (leader == null ? "offline" : leader.getName()) + " §7| §aMembers: §f" + String.join(", ", names) + " §7(" + party.members.size() + "/" + maximumSize + ")";
    }

    public void handleQuit(Player player) {
        invitationsByTarget.remove(player.getUniqueId());
        invitationsByTarget.values().removeIf(inv -> inv.leader.equals(player.getUniqueId()));
        leave(player);
    }

    private void broadcast(Party party, String message) {
        for (UUID id : party.members) { Player member = Bukkit.getPlayer(id); if (member != null) member.sendMessage(message); }
    }

    private static final class Party {
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private Party(UUID leader) { this.leader = leader; members.add(leader); }
    }
    private record Invitation(UUID leader, long expiresAt) { }
}
