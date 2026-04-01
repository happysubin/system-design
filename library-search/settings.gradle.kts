rootProject.name = "library-search"

include("external")
include("common")
include("search-api")
include("external:naver-client")
include("external:kakao-client")
findProject(":external:kakao-client")?.name = "kakao-client"
