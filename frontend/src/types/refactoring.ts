export type RefactoringSettings = { enabled: boolean };
export type RefactoringLocation = { 
    filePath?: string; 
    startLine?: number; 
    endLine?: number; 
    startColumn?: number; 
    endColumn?: number; 
    codeElement?: string; 
    description?: string 
};
export type DetectedRefactoring = { 
    id: number; 
    parent_sha: string; 
    type: string; 
    description: string; 
    before_locations: RefactoringLocation[]; 
    after_locations: RefactoringLocation[] 
};
export type RefactoringJob = { 
    id: number; 
    commit_sha: string; 
    status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"; 
    attempts: number; 
    last_error: string | null; 
    refactorings: DetectedRefactoring[] 
};
