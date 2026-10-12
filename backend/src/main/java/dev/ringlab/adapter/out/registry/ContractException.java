package dev.ringlab.adapter.out.registry;

import java.util.List;

public final class ContractException extends RuntimeException {
    public record Issue(String path, String code, String message) {}
    private final List<Issue> issues;
    public ContractException(String path, String code, String message) {
        this(List.of(new Issue(path, code, message)));
    }
    public ContractException(List<Issue> issues) {
        super("Contract validation failed");
        this.issues = List.copyOf(issues);
    }
    public List<Issue> issues() { return issues; }
}
