package com.anabada.neighbor.club.service;

import com.anabada.neighbor.club.domain.*;
import com.anabada.neighbor.club.domain.entity.Club;
import com.anabada.neighbor.club.domain.entity.Hobby;
import com.anabada.neighbor.club.repository.ClubRepository;
import com.anabada.neighbor.config.auth.PrincipalDetails;
import com.anabada.neighbor.file.domain.FileRequest;
import com.anabada.neighbor.file.domain.FileResponse;
import com.anabada.neighbor.file.service.FileService;
import com.anabada.neighbor.member.domain.Member;
import com.anabada.neighbor.used.domain.Likes;
import com.anabada.neighbor.used.domain.Post;
import com.anabada.neighbor.used.domain.Used;
import com.anabada.neighbor.used.repository.UsedRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class ClubServiceImpl implements ClubService {
    private final ClubRepository clubRepository;
    private final FileService fileService;
    private final UsedRepository usedRepository;

    public ClubServiceImpl(ClubRepository clubRepository, FileService fileService, UsedRepository usedRepository) {
        this.clubRepository = clubRepository;
        this.fileService = fileService;
        this.usedRepository = usedRepository;
    }

    @Transactional
    @Override/*클럽세이브*/
    public int saveClub(Club club) {//post,club 등록
        if (clubRepository.insertClub(club) == 1) {//db등록 실패시 0으로반환
            return 1;
        }
        return 0;
    }
    @Transactional
    @Override
    public long savePost(Post post) {
        if (clubRepository.insertPost(post) == 1){
            return post.getPostId();
        }//게시글이 성공적으로 등록되었으면 postId 반환 실패하였으면 -1반환
        return -1;
    }

    @Override
    public ClubResponse findClub(long postId, PrincipalDetails principalDetails) {
        Post post = clubRepository.selectPost(postId);
        Member postMember = clubRepository.selectMember(post.getMemberId());//글작성자의 정보
        int replyCount = usedRepository.findReplyCount(post.getPostId()); // postId 로 댓글 갯수 가져오기
        int likesCount = usedRepository.findLikesCount(post.getPostId()); // postId 로 좋아요 갯수 가져오기
        int likesCheck = 0; // 초기화

        Member member;
        if (principalDetails != null) { // 현재 로그인한 상태라면
            likesCheck = usedRepository.likesCheck(Likes.builder() // 좋아요를 누른 게시물인지 확인
                    .postId(postId)
                    .memberId(principalDetails.getMember().getMemberId())
                    .build());
            member = principalDetails.getMember(); // 글을 보러온 사용자의 정보
        }else {
            member = Member.builder().memberId(-2).build();
        }

        String[] splitString = postMember.getAddress().split(" ");
        String address = splitString[0] + " " + splitString[1];

        Club club = clubRepository.selectClub(postId);
//        System.out.println("클럽아이디" + club.getClubId()+ "멤버아이디 : " + member.getMemberId());
        return ClubResponse.builder()
                .clubId(club.getClubId())
                .clubJoinYn(clubRepository.selectClubJoinIdByMemberId(club.getClubId(), member.getMemberId()) == null ? 0 : 1) // 클럽에 가입되어있으면 1 아니면 0
                .postId(post.getPostId())
                .postUpdate(post.getPostUpdate())
                .memberId(postMember.getMemberId())
                .memberName(postMember.getMemberName())
                .title(post.getTitle())
                .content(post.getContent())
                .hobbyName(clubRepository.selectHobbyName(club.getHobbyId()))
                .score(postMember.getScore())
                .postView(post.getPostView())
                .replyCount(replyCount)
                .likesCount(likesCount)
                .likesCheck(likesCheck)
                .fileResponseList(fileService.findAllFileByPostId(postId))//여기까지완성
                .maxMan(club.getMaxMan())
                .nowMan(club.getNowMan())
                .address(address)
                .build();
    }

    /**
     * clubRequest -> Post 로 변환
     * @param clubRequest 사용자가 보낸 게시글
     * @param principalDetails 사용자 정보
     * @return Post 객체
     */
    @Override
    public Post clubRequestToPost(ClubRequest clubRequest
            , @AuthenticationPrincipal PrincipalDetails principalDetails) {
        Post post = Post.builder()
                .memberId(principalDetails.getMember().getMemberId())
                .title(clubRequest.getTitle())
                .content(clubRequest.getContent())
                .postType("club")
                .build();
        return post;
    }

    /**
     * clubRequest -> Club 으로 변환
     * @param clubRequest 사용자가 보낸 게시글
     * @param principalDetails 사용자 정보
     * @return Club 객체
     */
    @Override
    public Club clubRequestToClub(ClubRequest clubRequest
            , @AuthenticationPrincipal PrincipalDetails principalDetails) {
        Club club = Club.builder()
                .postId(clubRequest.getPostId())
                .memberId(principalDetails.getMember().getMemberId())
                .hobbyId(findHobbyId(clubRequest.getHobbyName()))
                .maxMan(clubRequest.getMaxMan())
                .build();
        return club;
    }

    @Transactional
    @Override
    public long updatePost(Post post) {
        clubRepository.updatePost(post);
        return post.getPostId();
    }

    @Transactional
    @Override
    public long updateClub(Club club) {
        clubRepository.updateClub(club);
        return club.getPostId();
    }

    @Override
    public long deletePost(long postId) {
        clubRepository.deletePost(postId);
        return postId;
    }

    @Override
    public Long findHobbyId(String hobbyName) {
        return clubRepository.selectHobbyId(hobbyName);
    }

    @Override
    public List<ClubResponse> findClubList(int num, long hobbyId, String search, String listType, long postId) {
        List<ClubResponse> result = new ArrayList<>(); //반환해줄 리스트생성
        List<Post> postList = clubRepository.selectPostList(); //foreach돌릴 postlist생성j

        List<Club> clubList = null;
        if (hobbyId != 0) {
            clubList = clubRepository.selectHobbyClubList(hobbyId);
        }else {
            clubList = clubRepository.selectClubList();
        }

        for (Club club : clubList) {
            Post post = clubRepository.selectPost(club.getPostId());
            if (post.getPostId() == postId) {
                continue;
            }
            Member member = clubRepository.selectMember(club.getMemberId());
            int replyCount = usedRepository.findReplyCount(post.getPostId());
            int likesCount = usedRepository.findLikesCount(post.getPostId());
            String[] splitString = member.getAddress().split(" ");
            String address = splitString[0] + " " + splitString[1];
            ClubResponse temp = ClubResponse.builder()
                    .postId(post.getPostId())
                    .memberId(member.getMemberId())
                    .memberName(member.getMemberName())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .hobbyName(clubRepository.selectHobbyName(club.getHobbyId()))
                    .score(member.getScore())
                    .maxMan(club.getMaxMan())
                    .nowMan(club.getNowMan())
                    .address(address)
                    .replyCount(replyCount)
                    .likesCount(likesCount)
                    .postView(post.getPostView())
                    .postUpdate(post.getPostUpdate())
                    .build();
            if (temp.getTitle().indexOf(search) != -1 || temp.getContent().indexOf(search) != -1) {
                result.add(temp);
            }
//            result.add(temp);
        }
        Comparator<ClubResponse> comparator = (use1, use2) -> Long.valueOf(
                        use1.getPostUpdate().getTime())
                .compareTo(use2.getPostUpdate().getTime());
        Collections.sort(result, comparator.reversed());

        if (listType.equals("similarList")) {
            return result.subList(0, Math.min(result.size(), 4));
        }
        if(num >= result.size()){
            return null;
        }
        return result.subList(num,Math.min(result.size(),num+6));
//        return result;
    }

    @Override
    public int checkPost(ClubRequest clubRequest) {//clubPost 정상값인지 체크 나중에memberId추가해야함
        if (clubRequest.getTitle() == null ||
                clubRequest.getContent() == null ||
                clubRequest.getHobbyName() == null ||
                clubRequest.getMaxMan() == null) {
            return 0;
        }
        return 1;
    }

    @Override
    public Long findClubJoinByMemberId(ClubResponse club, Long memberId) {
        return clubRepository.selectClubJoinIdByMemberId(club.getClubId(), memberId);
    }

    /**
     * 모임 가입하기
     * @param club 모임정보
     * @param principalDetails 사용자정보
     * @return 가득찼을때 -1 성공시 1 실패시 0
     */
    @Override
    public int joinClubJoin(ClubResponse club, PrincipalDetails principalDetails) {
        Long memberId = principalDetails.getMember().getMemberId();
        if (club.getMaxMan() == club.getNowMan()) {
            return -1; // 모임 최대인원이 가득찼을때 -1 리턴
        }
        return clubRepository.insertClubJoin(club.getClubId(), memberId, club.getPostId());
    }

    @Override
    public int deleteClubJoin(ClubResponse club, PrincipalDetails principalDetails) {
        Long memberId = principalDetails.getMember().getMemberId();
        return clubRepository.deleteClubJoin(club.getClubId(), memberId);
    }

    @Override
    public void updateNowMan(int num, Long clubId) {
        if (num == 1){
            clubRepository.updateNowManPlus(clubId);
        }else{
            clubRepository.updateNowManMinus(clubId);
        }
    }

    @Override
    public List<Hobby> findHobbyName() {
        return clubRepository.findHobbyName();
    }

    @Override
    public void updatePostView(Long postId) {
        usedRepository.updatePostView(postId);
    }

    @Override
    public List<ClubResponse> mainList() {
        List<ClubResponse> result = new ArrayList<>();
        List<Post> postList = clubRepository.selectHotPostList();
        for (Post post : postList) {
            Club club = clubRepository.selectClub(post.getPostId());
            Member member = clubRepository.selectMember(post.getMemberId());
            int replyCount = usedRepository.findReplyCount(post.getPostId());
            int likesCount = usedRepository.findLikesCount(post.getPostId());
            String[] splitString = member.getAddress().split(" ");
            String address = splitString[0] + " " + splitString[1];
            ClubResponse temp = ClubResponse.builder()
                    .postId(post.getPostId())
                    .memberId(member.getMemberId())
                    .memberName(member.getMemberName())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .hobbyName(clubRepository.selectHobbyName(club.getHobbyId()))
                    .score(member.getScore())
                    .maxMan(club.getMaxMan())
                    .nowMan(club.getNowMan())
                    .address(address)
                    .replyCount(replyCount)
                    .likesCount(likesCount)
                    .postView(post.getPostView())
                    .postUpdate(post.getPostUpdate())
                    .build();
            result.add(temp);
        }
        return result;
    }
}
