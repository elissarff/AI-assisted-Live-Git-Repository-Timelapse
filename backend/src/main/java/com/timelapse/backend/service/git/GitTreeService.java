package com.timelapse.backend.service.git;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.RepositoryTreeDto;
import com.timelapse.backend.dto.RepositoryTreeNodeDto;

/** Reads the authoritative file/folder snapshot stored by Git for a commit. */
@Service
public class GitTreeService {

    public RepositoryTreeDto getTree(Repository repository, RevCommit commit) throws Exception {
        MutableNode root = new MutableNode("", "", "DIRECTORY");

        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(commit.getTree());
            walk.setRecursive(true);

            while (walk.next()) {
                String path = walk.getPathString();
                String[] parts = path.split("/");
                MutableNode current = root;
                StringBuilder currentPath = new StringBuilder();

                for (int i = 0; i < parts.length; i++) {
                    if (currentPath.length() > 0) currentPath.append('/');
                    currentPath.append(parts[i]);
                    boolean leaf = i == parts.length - 1;
                    String type = leaf ? typeOf(walk.getFileMode(0)) : "DIRECTORY";
                    current = current.child(parts[i], currentPath.toString(), type);
                }
            }
        }

        return new RepositoryTreeDto(commit.getName(), root.toDto());
    }

    private String typeOf(FileMode mode) {
        if (FileMode.GITLINK.equals(mode)) return "SUBMODULE";
        if (FileMode.SYMLINK.equals(mode)) return "SYMLINK";
        return "FILE";
    }

    private static final class MutableNode {
        private final String name;
        private final String path;
        private final String type;
        private final Map<String, MutableNode> children = new LinkedHashMap<>();

        private MutableNode(String name, String path, String type) {
            this.name = name;
            this.path = path;
            this.type = type;
        }

        private MutableNode child(String name, String path, String type) {
            return children.computeIfAbsent(name, ignored -> new MutableNode(name, path, type));
        }

        private RepositoryTreeNodeDto toDto() {
            List<RepositoryTreeNodeDto> childDtos = new ArrayList<>();
            children.values().stream()
                    .sorted(Comparator.comparing((MutableNode n) -> !"DIRECTORY".equals(n.type))
                            .thenComparing(n -> n.name.toLowerCase()))
                    .map(MutableNode::toDto)
                    .forEach(childDtos::add);
            return new RepositoryTreeNodeDto(name, path, type, List.copyOf(childDtos));
        }
    }
}
