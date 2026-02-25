package com.banking.platform.shared.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    // 5-arg constructor (used by AchService, WireService)
    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = (page + 1) >= totalPages;
    }

    // 6-arg constructor (used by BankService, OnboardingService)
    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    // Alias getters for RewardsService compatibility
    public int getPageNumber() { return page; }
    public int getPageSize() { return size; }
    public boolean isIsLast() { return last; }

    public static <T> PagedResponseBuilder<T> builder() {
        return new PagedResponseBuilder<>();
    }

    public static <T> PagedResponse<T> from(Page<T> pageData) {
        return new PagedResponse<>(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isLast()
        );
    }

    public static <T> PagedResponse<T> of(Page<T> pageData, int pageNum, int size, long totalElements, int totalPages) {
        return new PagedResponse<>(
                pageData.getContent(),
                pageNum,
                size,
                totalElements,
                totalPages
        );
    }

    public static class PagedResponseBuilder<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;

        public PagedResponseBuilder<T> content(List<T> content) { this.content = content; return this; }
        public PagedResponseBuilder<T> page(int page) { this.page = page; return this; }
        public PagedResponseBuilder<T> size(int size) { this.size = size; return this; }
        public PagedResponseBuilder<T> totalElements(long totalElements) { this.totalElements = totalElements; return this; }
        public PagedResponseBuilder<T> totalPages(int totalPages) { this.totalPages = totalPages; return this; }
        public PagedResponseBuilder<T> last(boolean last) { this.last = last; return this; }

        // Aliases for RewardsService
        public PagedResponseBuilder<T> pageNumber(int pageNumber) { this.page = pageNumber; return this; }
        public PagedResponseBuilder<T> pageSize(int pageSize) { this.size = pageSize; return this; }
        public PagedResponseBuilder<T> isLast(boolean isLast) { this.last = isLast; return this; }

        public PagedResponse<T> build() {
            return new PagedResponse<>(content, page, size, totalElements, totalPages, last);
        }
    }
}
